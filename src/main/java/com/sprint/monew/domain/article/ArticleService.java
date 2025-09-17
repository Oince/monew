package com.sprint.monew.domain.article;

import static com.sprint.monew.common.batch.support.CustomExecutionContextKeys.ARTICLE_IDS;

import com.sprint.monew.common.util.CursorPageResponseDto;
import com.sprint.monew.domain.activity.UserActivityService;
import com.sprint.monew.domain.article.articleview.ArticleView;
import com.sprint.monew.domain.article.articleview.ArticleViewRepository;
import com.sprint.monew.domain.article.dto.ArticleDto;
import com.sprint.monew.domain.article.dto.ArticleRestoreResultDto;
import com.sprint.monew.domain.article.dto.ArticleViewDto;
import com.sprint.monew.domain.article.dto.ArticleCondition;
import com.sprint.monew.domain.article.exception.ArticleViewAlreadyExistException;
import com.sprint.monew.domain.article.repository.ArticleRepository;
import com.sprint.monew.domain.comment.repository.CommentRepository;
import com.sprint.monew.domain.user.User;
import com.sprint.monew.domain.user.UserRepository;
import com.sprint.monew.global.error.ErrorCode;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleService {

  @Resource(name = "articleRestoreJob")
  private final Job articleRestoreJob;
  private final JobLauncher jobLauncher;
  private final ArticleRepository articleRepository;
  private final UserRepository userRepository;
  private final CommentRepository commentRepository;
  private final ArticleViewRepository articleViewRepository;
  private final UserActivityService userActivityService;

  public ArticleViewDto registerArticleView(UUID id, UUID userId) {
    User user = userRepository.findByIdAndDeletedFalse(userId)
        .orElseThrow(() -> new IllegalArgumentException(ErrorCode.USER_NOT_FOUND.getMessage()));
    Article article = articleRepository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new IllegalArgumentException(ErrorCode.ARTICLE_NOT_FOUND.getMessage()));

    if (articleViewRepository.existsByUserAndArticle(user, article)) {
      throw ArticleViewAlreadyExistException.withId(user.getId(), article.getId());
    }

    ArticleView articleView = ArticleView.create(user, article);
    articleViewRepository.save(articleView);

    userActivityService.synchronizeUserActivityToMongo(userId);

    long commentCount = commentRepository.countByArticleAndDeletedFalse(article);
    long viewCount = articleViewRepository.countByArticle(article);

    return ArticleViewDto.from(articleView, commentCount, viewCount);
  }

  @Transactional(readOnly = true)
  public CursorPageResponseDto<ArticleDto> getArticles(
      ArticleCondition articleCondition, Pageable pageable, UUID userId) {
    Slice<ArticleDto> page = articleRepository.getArticles(articleCondition, userId, pageable);
    Long totalElements = articleRepository.getArticleCount(articleCondition);

    List<ArticleDto> content = page.getContent();
    Sort sort = page.getSort();
    Object nextCursor = null;
    Instant after = null;

    if (!content.isEmpty()) {
      String property = sort.iterator().next().getProperty();
      if (property.equals("publishDate")) {
        nextCursor = content.get(content.size() - 1).publishDate();
      } else if (property.equals("commentCount")) {
        nextCursor = content.get(content.size() - 1).commentCount();
      } else {
        nextCursor = content.get(content.size() - 1).viewCount();
      }
      after = content.get(content.size() - 1).createdAt();
    }

    return new CursorPageResponseDto<>(
        page.getContent(),
        nextCursor,
        after,
        page.getSize(),
        totalElements,
        page.hasNext()
    );
  }

  @Transactional(readOnly = true)
  public Page<ArticleDto> getArticlesWithOffset(
      ArticleCondition articleCondition, Pageable pageable, UUID userId) {
    return articleRepository.getArticlesWithOffset(articleCondition, userId, pageable);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public List<ArticleRestoreResultDto> restoreArticle(Instant from, Instant to)
      throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {

    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("time", System.currentTimeMillis())
        .addString("from", from.toString())
        .addString("to", to.toString())
        .toJobParameters();

    log.info("Article Restore Start");
    JobExecution jobExecution = jobLauncher.run(articleRestoreJob, jobParameters);

    log.info("Article Restore End");
    ExecutionContext jobContext = jobExecution.getExecutionContext();
    List<UUID> articleIds = (List<UUID>) jobContext.get(ARTICLE_IDS.getKey());

    long restoredArticleSize = articleIds == null ? 0 : articleIds.size();

    ArticleRestoreResultDto resultDto = new ArticleRestoreResultDto(
        Instant.now(),
        articleIds,
        restoredArticleSize
    );
    List<ArticleRestoreResultDto> result = List.of(resultDto);
    jobContext.remove(ARTICLE_IDS.getKey());
    return result;
  }

  public void deleteArticle(UUID id) {
    articleRepository.findById(id).ifPresent(Article::logicallyDelete);
  }

  public void hardDeleteArticle(UUID id) {
    articleRepository.findById(id).ifPresent(articleRepository::delete);
  }
}
