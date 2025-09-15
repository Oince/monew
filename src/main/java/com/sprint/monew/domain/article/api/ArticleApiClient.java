package com.sprint.monew.domain.article.api;

import com.sprint.monew.domain.article.Article.Source;
import com.sprint.monew.domain.article.api.chosun.ChosunArticleClient;
import com.sprint.monew.domain.article.api.chosun.ChosunArticleClient.ChosunCategory;
import com.sprint.monew.domain.article.api.chosun.ChosunArticleResponse;
import com.sprint.monew.domain.article.api.hankyung.HankyungArticleClient;
import com.sprint.monew.domain.article.api.hankyung.HankyungArticleClient.HankyungCategory;
import com.sprint.monew.domain.article.api.hankyung.HankyungArticleResponse;
import com.sprint.monew.domain.article.api.naver.NaverArticleClient;
import com.sprint.monew.domain.article.api.naver.NaverArticleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

public interface ArticleApiClient {

  /**
   * @param display 한 번에 표시할 검색 결과 개수(기본값:10, 최댓값: 100)
   * @param start 검색 시작 위치(기본값: 1, 최댓값: 1000)
   */
  List<ArticleApiDto> getNaverArticle(int display, int start);

  List<ArticleApiDto> getNaverArticle();

  /**
   * @param category 카테고리 별로 가져올 수 있어요.
   *                 ALL로 하시면 카테고리 구별 없이 가져올 수 있어요.
   */
  List<ArticleApiDto> getChosunArticle(ChosunCategory category);

  /**
   * @param category 카테고리 별로 가져올 수 있어요.
   *                 ALL로 하시면 카테고리 구별 없이 가져올 수 있어요.
   */
  List<ArticleApiDto> getHankyungArticle(HankyungCategory category);
}
