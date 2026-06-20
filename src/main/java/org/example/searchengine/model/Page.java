package org.example.searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.*;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(name="uk_site_path", columnNames={"site_id", "path"})})
@NoArgsConstructor(onConstructor_ = { @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) })
@RequiredArgsConstructor
public class Page implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false)
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_page_site"))
    @NonNull
    private Site site;
    @Column(name = "path")
    @NonNull
    @Pattern(regexp = "(/[\\S&&[^/]]+)*(/[\\S&&[^/.]]+)(.htm(l)?)?", message = "Correct page address")
    private String path;
    @Column(name = "code")
    @NonNull
    private Integer code;
    @Column(name = "content", columnDefinition = "mediumtext", nullable = false)
    @NonNull
    private String content;
    @JsonIgnore
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "page", orphanRemoval = true, cascade = CascadeType.REMOVE)
    private final List<org.example.searchengine.model.Index> indices = new ArrayList<>();
}
