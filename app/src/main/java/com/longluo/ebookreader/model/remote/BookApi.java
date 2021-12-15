package com.longluo.ebookreader.model.remote;

import com.longluo.ebookreader.model.bean.BookDetailBean;
import com.longluo.ebookreader.model.bean.packages.BookChapterPackage;
import com.longluo.ebookreader.model.bean.packages.BookHelpsPackage;
import com.longluo.ebookreader.model.bean.packages.BookListDetailPackage;
import com.longluo.ebookreader.model.bean.packages.BookListPackage;
import com.longluo.ebookreader.model.bean.packages.BookReviewPackage;
import com.longluo.ebookreader.model.bean.packages.BookSortPackage;
import com.longluo.ebookreader.model.bean.packages.BookSubSortPackage;
import com.longluo.ebookreader.model.bean.packages.BookTagPackage;
import com.longluo.ebookreader.model.bean.packages.ChapterInfoPackage;
import com.longluo.ebookreader.model.bean.packages.HelpsDetailPackage;
import com.longluo.ebookreader.model.bean.packages.HotWordPackage;
import com.longluo.ebookreader.model.bean.packages.KeyWordPackage;
import com.longluo.ebookreader.model.bean.packages.RecommendBookListPackage;
import com.longluo.ebookreader.model.bean.packages.RecommendBookPackage;
import com.longluo.ebookreader.model.bean.packages.ReviewDetailPackage;
import com.longluo.ebookreader.model.bean.packages.SearchBookPackage;
import com.longluo.ebookreader.model.bean.packages.SortBookPackage;

import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;


public interface BookApi {
    /**
     * 推荐书籍
     *
     * @param gender
     * @return
     */
    @GET("/book/recommend")
    Single<RecommendBookPackage> getRecommendBookPackage(@Query("gender") String gender);

    /**
     * 获取书籍的章节总列表
     *
     * @param bookId
     * @param view   默认参数为:chapters
     * @return
     */
    @GET("/mix-atoc/{bookId}")
    Single<BookChapterPackage> getBookChapterPackage(@Path("bookId") String bookId, @Query("view") String view);

    /**
     * 章节的内容
     * 这里采用的是同步请求。
     *
     * @param url
     * @return
     */
    @GET("http://chapter2.zhuishushenqi.com/chapter/{url}")
    Single<ChapterInfoPackage> getChapterInfoPackage(@Path("url") String url);

    /*******************************Community *******************************************************/

    /**
     * 获取书荒区帖子列表
     * 全部、默认排序  http://api.zhuishushenqi.com/post/help?duration=all&sort=updated&start=0&limit=20&distillate=
     * 精品、默认排序  http://api.zhuishushenqi.com/post/help?duration=all&sort=updated&start=0&limit=20&distillate=true
     *
     * @param duration   all
     * @param sort       updated(默认排序)
     *                   created(最新发布)
     *                   comment-count(最多评论)
     * @param start      0
     * @param limit      20
     * @param distillate true(精品) 、空字符（全部）
     * @return
     */
    @GET("/post/help")
    Single<BookHelpsPackage> getBookHelpList(@Query("duration") String duration, @Query("sort") String sort, @Query("start") String start, @Query("limit") String limit, @Query("distillate") String distillate);

    /**
     * 获取书评区帖子列表
     * 全部、全部类型、默认排序  http://api.zhuishushenqi.com/post/review?duration=all&sort=updated&type=all&start=0&limit=20&distillate=
     * 精品、玄幻奇幻、默认排序  http://api.zhuishushenqi.com/post/review?duration=all&sort=updated&type=xhqh&start=0&limit=20&distillate=true
     *
     * @param duration   all
     * @param sort       updated(默认排序)
     *                   created(最新发布)
     *                   helpful(最有用的)
     *                   comment-count(最多评论)
     * @param type       all(全部类型)、xhqh(玄幻奇幻)、dsyn(都市异能)...
     * @param start      0
     * @param limit      20
     * @param distillate true(精品) 、空字符（全部）
     * @return
     */
    @GET("/post/review")
    Single<BookReviewPackage> getBookReviewList(@Query("duration") String duration, @Query("sort") String sort, @Query("type") String type, @Query("start") String start, @Query("limit") String limit, @Query("distillate") String distillate);


    /***********************************帖子详情*******************************************************/

    /**
     * 获取书评区帖子详情
     *
     * @param detailId->_id
     * @return
     */
    @GET("/post/review/{detailId}")
    Single<ReviewDetailPackage> getReviewDetailPacakge(@Path("detailId") String detailId);


    /**
     * 获取书荒区帖子详情
     *
     * @param detailId->_id
     * @return
     **/
    @GET("/post/help/{detailId}")
    Single<HelpsDetailPackage> getHelpsDetailPackage(@Path("detailId") String detailId);

    /************************************find****************************************************/

    /*******************************分类***************************************/
    /**
     * 获取分类
     *
     * @return
     */
    @GET("/cats/lv2/statistics")
    Single<BookSortPackage> getBookSortPackage();

    /**
     * 获取二级分类
     *
     * @return
     */
    @GET("/cats/lv2")
    Single<BookSubSortPackage> getBookSubSortPackage();

    /**
     * 按分类获取书籍列表
     *
     * @param gender male、female
     * @param type   hot(热门)、new(新书)、reputation(好评)、over(完结)
     * @param major  玄幻
     * @param minor  东方玄幻、异界大陆、异界争霸、远古神话
     * @param limit  50
     * @return
     */
    @GET("/book/by-categories")
    Single<SortBookPackage> getSortBookPackage(@Query("gender") String gender, @Query("type") String type, @Query("major") String major, @Query("minor") String minor, @Query("start") int start, @Query("limit") int limit);

    /********************************主题书单**************************************8*/

    /**
     * 获取主题书单列表
     * 本周最热：duration=last-seven-days&sort=collectorCount
     * 最新发布：duration=all&sort=created
     * 最多收藏：duration=all&sort=collectorCount
     * <p>
     * 如:http://api.zhuishushenqi.com/book-list?duration=last-seven-days&sort=collectorCount&start=0&limit=20&tag=%E9%83%BD%E5%B8%82&gender=male
     *
     * @param tag    都市、古代、架空、重生、玄幻、网游
     * @param gender male、female
     * @param limit  20
     * @return
     */
    @GET("/book-list")
    Single<BookListPackage> getBookListPackage(@Query("duration") String duration, @Query("sort") String sort,
                                               @Query("start") String start, @Query("limit") String limit,
                                               @Query("tag") String tag, @Query("gender") String gender);

    /**
     * 获取主题书单标签列表
     *
     * @return
     */
    @GET("/book-list/tagType")
    Single<BookTagPackage> getBookTagPackage();

    /**
     * 获取书单详情
     *
     * @return
     */
    @GET("/book-list/{bookListId}")
    Single<BookListDetailPackage> getBookListDetailPackage(@Path("bookListId") String bookListId);


    /*************************书籍详情**********************************/

    /**
     * 书籍推荐书单
     *
     * @param bookId
     * @param limit
     * @return
     */
    @GET("/book-list/{bookId}/recommend")
    Single<RecommendBookListPackage> getRecommendBookListPackage(@Path("bookId") String bookId, @Query("limit") String limit);

    /**
     * 书籍详情
     *
     * @param bookId
     * @return
     */
    @GET("/book/{bookId}?t=0&useNewCat=true")
    Single<BookDetailBean> getBookDetail(@Path("bookId") String bookId);

    /************************************搜索书籍******************************************************/
    @GET("/book/hot-word")
    Single<HotWordPackage> getHotWordPackage();

    /**
     * 关键字自动补全
     *
     * @param query
     * @return
     */
    @GET("/book/auto-complete")
    Single<KeyWordPackage> getKeyWordPacakge(@Query("query") String query);

    /**
     * 书籍查询
     *
     * @param query:作者名或者书名
     * @return
     */
    @GET("/book/fuzzy-search")
    Single<SearchBookPackage> getSearchBookPackage(@Query("query") String query);
}
