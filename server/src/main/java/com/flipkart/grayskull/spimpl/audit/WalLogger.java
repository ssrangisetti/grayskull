package com.flipkart.grayskull.spimpl.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.models.db.AuditEntry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
@Slf4j
public class WalLogger {

    private static final String FILE_NAME_PREFIX = "audit.";
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("audit.\\d{20}");

    private final String auditFolder;
    private final int maxLines;
    private final ObjectMapper objectMapper;

    private long counter;
    private FileWriter fileWriter;

    public WalLogger(String auditFolder, int maxLines, ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        Path auditFolderPath = Paths.get(auditFolder);
        if (!Files.exists(auditFolderPath) || !Files.isDirectory(auditFolderPath)) {
            throw new IllegalArgumentException("Invalid audit folder: " + auditFolder);
        }
        this.auditFolder = auditFolder;
        this.maxLines = maxLines;
        Path curFile = currentFile(auditFolderPath);
        this.counter = maxEventId(curFile);
        fileWriter = new FileWriter(curFile.toFile(), true);
        fileWriter.write("");
    }

    private Path filePath(String auditFolder, long entryNum) {
        return Paths.get(auditFolder, "%s%020d".formatted(FILE_NAME_PREFIX, entryNum));
    }

    private Path currentFile(Path auditFolderPath) throws IOException {
        try (var files = Files.list(auditFolderPath)) {
            return files
                    .filter(path -> FILE_NAME_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .reduce(WalLogger::lastElementReducer)
                    .orElse(filePath(auditFolderPath.toString(), 0));
        }
    }

    private long maxEventId(Path filePath) throws IOException {
        long fileNum = Long.parseLong(filePath.getFileName().toString().substring(FILE_NAME_PREFIX.length()));
        if (!Files.exists(filePath)) {
            return fileNum;
        }
        try (var lines = Files.lines(filePath)) {
            Optional<String> lastLine = lines.reduce(WalLogger::lastElementReducer);
            return lastLine.map(String::trim).map(s -> s.split(",", 2)[0]).map(Long::parseLong).orElse(fileNum);
        }
    }

    public synchronized long write(AuditEntry auditEntry) throws IOException {
        long currCount = ++counter;
        fileWriter.write(currCount + ",");
        fileWriter.write(objectMapper.writeValueAsString(auditEntry));
        fileWriter.write("\n");
        fileWriter.flush();
        if (currCount % maxLines == 0) {
            fileWriter.close();
            fileWriter = new FileWriter(filePath(auditFolder, currCount).toFile());
            fileWriter.write("");
            fileWriter.flush();
        }
        return currCount;
    }

    public void cleanOldFiles(long latestEntry) throws IOException {
        if (latestEntry > counter) {
            throw new IllegalArgumentException("deleting more than latest existing entry: " + counter);
        }
        Path auditFolderPath = Path.of(auditFolder);
        List<Path> walFiles;
        try (var files = Files.list(auditFolderPath)) {
            walFiles = files
                    .filter(path -> FILE_NAME_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
        List<Path> walFilesToDelete = new ArrayList<>();
        for (Path walFile : walFiles) {
            try (var lines = Files.lines(walFile)) {
                var lastLine = lines.reduce(WalLogger::lastElementReducer);
                if (lastLine.isPresent()) {
                    String entry = lastLine.get().split(",", 2)[0];
                    long entryNum = Long.parseLong(entry);
                    if (entryNum < latestEntry) {
                        walFilesToDelete.add(walFile);
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading file {}, not processing it", walFile, e);
            }
        }
        for (Path walFile : walFilesToDelete) {
            Files.deleteIfExists(walFile);
        }
    }

    public Stream<AuditOrTick.Audit> backlogAudits(long lastEntry) throws IOException {
        Path auditFolderPath = Path.of(auditFolder);
        Stream<Path> walFiles;
        try (var files = Files.list(auditFolderPath)) {
            walFiles = files
                    .filter(path -> path.getFileName().toString().startsWith(FILE_NAME_PREFIX))
                    .sorted()
                    .toList()
                    .stream();
        }
        return walFiles
                .flatMap(ThrowingFunction.toFunction(Files::lines))
                .map(ThrowingFunction.toFunction(this::fromLine))
                .filter(audit -> audit.counter() > lastEntry);
    }

    private AuditOrTick.Audit fromLine(String line) throws JsonProcessingException {
        String[] split = line.split(",", 2);
        long entryNum = Long.parseLong(split[0]);
        AuditEntry auditEntry = objectMapper.readValue(split[1], AuditEntry.class);
        return new AuditOrTick.Audit(auditEntry, entryNum);
    }

    private static <T> T lastElementReducer(T first, T second) {
        return second;
    }

    public interface ThrowingFunction<T, R, E extends Exception> {
        R apply(T t) throws E;

        private static <K, V, E extends Exception> Function<K, V> toFunction(ThrowingFunction<K, V, E> function) {
            return new Function<>() {
                @Override
                @SneakyThrows
                public V apply(K k) {
                    return function.apply(k);
                }
            };
        }

    }
}
