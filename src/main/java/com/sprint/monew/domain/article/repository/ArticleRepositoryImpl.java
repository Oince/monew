package com.sprint.monew.domain.article.repository;

import static com.sprint.monew.domain.article.QArticle.article;
import static com.sprint.monew.domain.article.articleinterest.QArticleInterest.articleInterest;
import static com.sprint.monew.domain.comment.QComment.comment;
import static com.sprint.monew.domain.interest.QInterest.interest;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.monew.domain.article.articleview.QArticleView;
import com.sprint.monew.domain.article.dto.ArticleDto;
import com.sprint.monew.domain.article.dto.ArticleCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public class ArticleRepositoryImpl implements ArticleRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public Slice<ArticleDto> getArticles(ArticleCondition condition, UUID userId, Pageable pageable) {
    QArticleView viewAll = new QArticleView("viewAll");
    QArticleView viewMe = new QArticleView("viewMe");
    List<ArticleDto> result = queryFactory
        .select(Projections.constructor(
            ArticleDto.class, article.id, article.createdAt, article.source.stringValue(), article.sourceUrl, article.title,
            article.publishDate, article.summary, comment.countDistinct(), viewAll.countDistinct(), viewMe.id.isNotNull())
        )
        .from(article)
        .leftJoin(comment).on(comment.article.eq(article).and(comment.deleted.isFalse()))
        .leftJoin(article.articleViews, viewAll)
        .leftJoin(article.articleViews, viewMe).on(viewMe.user.id.eq(userId))
        .leftJoin(article.articleInterests, articleInterest)
        .leftJoin(articleInterest.interest, interest)
        .where(
            article.deleted.isFalse(),
            searchKeyword(condition.keyword()),
            interestIdEq(condition.interestId()),
            sourceConditionIn(condition.sourceIn()),
            publishDateFrom(condition.publishDateFrom()),
            publishDateTo(condition.publishDateTo()),
            publishDateCursor(condition.cursor(), condition.after(), pageable.getSort())
        )
        .groupBy(
            article.id, article.createdAt, article.source, article.sourceUrl,
            article.title, article.publishDate, article.summary, viewMe.id
        )
        .having(
            commentCountCursor(condition.cursor(), condition.after(), pageable.getSort()),
            viewCountCursor(condition.cursor(), condition.after(), pageable.getSort(), viewAll)
        )
        .orderBy(getOrderSpecifiers(pageable.getSort(), viewAll))
        .limit(pageable.getPageSize() + 1)
        .fetch();

    boolean hasNext = result.size() > pageable.getPageSize();
    if (hasNext) {
      result.remove(result.size() - 1);
    }

    return new SliceImpl<>(result, pageable, hasNext);
  }

  @Override
  public Page<ArticleDto> getArticlesWithOffset(ArticleCondition condition, UUID userId, Pageable pageable) {
    QArticleView viewAll = new QArticleView("viewAll");
    QArticleView viewMe = new QArticleView("viewMe");
    List<ArticleDto> result = queryFactory
        .select(Projections.constructor(
            ArticleDto.class, article.id, article.createdAt, article.source.stringValue(), article.sourceUrl, article.title,
            article.publishDate, article.summary, comment.countDistinct(), viewAll.countDistinct(), viewMe.id.isNotNull())
        )
        .from(article)
        .leftJoin(comment).on(comment.article.eq(article).and(comment.deleted.isFalse()))
        .leftJoin(article.articleViews, viewAll)
        .leftJoin(article.articleViews, viewMe).on(viewMe.user.id.eq(userId))
        .leftJoin(article.articleInterests, articleInterest)
        .leftJoin(articleInterest.interest, interest)
        .where(
            article.deleted.isFalse(),
            searchKeyword(condition.keyword()),
            interestIdEq(condition.interestId()),
            sourceConditionIn(condition.sourceIn()),
            publishDateFrom(condition.publishDateFrom()),
            publishDateTo(condition.publishDateTo())
        )
        .groupBy(
            article.id, article.createdAt, article.source, article.sourceUrl,
            article.title, article.publishDate, article.summary, viewMe.id
        )
        .orderBy(getOrderSpecifiers(pageable.getSort(), viewAll))
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long total = getArticleCount(condition);

    return new PageImpl<>(result, pageable, total);
  }

  public List<String> findAllSourceUrl() {
    return queryFactory
        .select(article.sourceUrl)
        .from(article)
        .fetch();
  }

  public Long getArticleCount(ArticleCondition condition) {
    return queryFactory
        .select(article.id.countDistinct())
        .from(article)
        .leftJoin(article.articleInterests, articleInterest)
        .leftJoin(articleInterest.interest, interest)
        .where(
            article.deleted.isFalse(),
            searchKeyword(condition.keyword()),
            interestIdEq(condition.interestId()),
            sourceConditionIn(condition.sourceIn()),
            publishDateFrom(condition.publishDateFrom()),
            publishDateTo(condition.publishDateTo())
        )
        .fetchOne();
  }

  private BooleanExpression searchKeyword(String keyword) {
    return keyword == null ? null :
        article.summary.containsIgnoreCase(keyword).or(article.title.containsIgnoreCase(keyword));
  }

  private BooleanExpression interestIdEq(UUID interestId) {
    return interestId == null ? null : interest.id.eq(interestId);
  }

  private BooleanExpression sourceConditionIn(List<String> sourceIn) {
    return sourceIn == null || sourceIn.isEmpty() ? null : article.source.stringValue().in(sourceIn);
  }

  private BooleanExpression publishDateFrom(Instant from) {
    return from == null ? null : article.publishDate.goe(from);
  }

  private BooleanExpression publishDateTo(Instant to) {
    return to == null ? null : article.publishDate.loe(to);
  }

  private BooleanExpression publishDateCursor(String cursor, Instant after, Sort sort) {
    Sort.Order order = sort.iterator().next();
    String property = order.getProperty();
    if (cursor == null || after == null || !property.equals("publishDate")) {
      return null;
    }

    Instant publishDateCursor = Instant.parse(cursor);

    BooleanExpression primary = order.isAscending() ?
        article.publishDate.gt(publishDateCursor) :
        article.publishDate.lt(publishDateCursor);

    return primary.or(article.publishDate.eq(publishDateCursor).and(article.createdAt.lt(after)));
  }

  private BooleanExpression commentCountCursor(String cursor, Instant after, Sort sort) {
    Sort.Order order = sort.iterator().next();
    String property = order.getProperty();
    if (cursor == null || after == null || !property.equals("commentCount")) {
      return null;
    }

    long commentCountCursor = Long.parseLong(cursor);
    NumberExpression<Long> commentCount = comment.count();

    BooleanExpression primary = order.isAscending() ?
        commentCount.gt(commentCountCursor) :
        commentCount.lt(commentCountCursor);

    return primary.or(commentCount.eq(commentCountCursor).and(article.createdAt.lt(after)));
  }

  private BooleanExpression viewCountCursor(String cursor, Instant after, Sort sort,
      QArticleView viewAll) {

    Sort.Order order = sort.iterator().next();
    String property = order.getProperty();
    if (cursor == null || after == null || !property.equals("viewCount")) {
      return null;
    }

    long viewCountCursor = Long.parseLong(cursor);
    NumberExpression<Long> viewCount = viewAll.count();

    BooleanExpression primary = order.isAscending() ?
        viewCount.gt(viewCountCursor) :
        viewCount.lt(viewCountCursor);

    return primary.or(viewCount.eq(viewCountCursor).and(article.createdAt.lt(after)));
  }

  private OrderSpecifier<?>[] getOrderSpecifiers(Sort sort, QArticleView viewAll) {
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    Sort.Order order = sort.iterator().next();
    Order direction = order.isAscending() ? Order.ASC : Order.DESC;
    String property = order.getProperty();

    if (property.equals("commentCount")) {
      orders.add(new OrderSpecifier<>(direction, comment.count()));
    } else if (property.equals("viewCount")) {
      orders.add(new OrderSpecifier<>(direction, viewAll.count()));
    } else {
      orders.add(new OrderSpecifier<>(direction, article.publishDate));
    }
    orders.add(new OrderSpecifier<>(Order.DESC, article.createdAt));
    return orders.toArray(new OrderSpecifier[0]);
  }
}
