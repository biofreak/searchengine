package org.example.searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Getter
@Table(name = "index", uniqueConstraints={@UniqueConstraint(name="uk_page_lemma", columnNames={"page_id", "lemma_id"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
public class Index implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "page_id", foreignKey = @ForeignKey(name = "fk_index_page"), nullable = false)
    private Page page;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "lemma_id", foreignKey = @ForeignKey(name = "fk_index_lemma"), nullable = false)
    private Lemma lemma;
    @Column(name = "rank", nullable = false)
    private Float rank;
}
