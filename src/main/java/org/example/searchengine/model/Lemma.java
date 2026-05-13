package org.example.searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.io.Serializable;

@Entity
@Getter
@Table(name = "lemma", uniqueConstraints = {@UniqueConstraint(name="uk_site_lemma", columnNames={"site_id", "lemma"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
@RequiredArgsConstructor
public class Lemma implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_lemma_site"), nullable = false)
    @NonNull
    private Site site;
    @Column(name = "lemma", nullable = false)
    @NonNull
    private String lemma;
    @Column(name = "frequency", insertable = false)
    @ColumnDefault("0")
    private int frequency;
}
