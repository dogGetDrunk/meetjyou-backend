package com.dogGetDrunk.meetjyou.post;

import com.dogGetDrunk.meetjyou.party.Party;
import com.dogGetDrunk.meetjyou.plan.Plan;
import com.dogGetDrunk.meetjyou.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime lastEditedAt;

    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    private int postStatus;

    private String title;
    private String body;
    private String preview;
    private int views;

    @ManyToOne
    private User author;

    @ManyToOne
    private Party party;

    @ManyToOne
    private Plan plan;
}
