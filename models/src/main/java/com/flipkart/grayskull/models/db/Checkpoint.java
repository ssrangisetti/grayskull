package com.flipkart.grayskull.models.db;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@NoArgsConstructor
public class Checkpoint {
    @Id
    private String id;

    private String name;

    private long lines;

    public Checkpoint(String name) {
        this.name = name;
    }
}
