package com.dogGetDrunk.meetjyou.post;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    public List<PostResponseDto> getPostsByAuthorId(Long authorId) {
        List<Post> posts = postRepository.findAllByAuthor_Id(authorId);

        return posts.stream()
                .map(post -> PostResponseDto.builder()
                        .createdAt(post.getCreatedAt())
                        .lastEditedAt(post.getLastEditedAt())
                        .postStatus(post.getPostStatus())
                        .title(post.getTitle())
                        .body(post.getBody())
                        .preview(post.getPreview())
                        .views(post.getViews())
                        .build())
                .collect(Collectors.toList());
    }
}
