package com.sprint.monew.domain.article.repository;

import com.sprint.monew.domain.article.dto.ArticleDto;
import com.sprint.monew.domain.article.dto.ArticleCondition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ArticleRepositoryCustom {

  Slice<ArticleDto> getArticles(ArticleCondition condition, UUID userId, Pageable pageable);

  Page<ArticleDto> getArticlesWithOffset(ArticleCondition condition, UUID userId, Pageable pageable);

  List<String> findAllSourceUrl();

  Long getArticleCount(ArticleCondition condition);
}