package com.fanhl.doujinMoe.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import com.fanhl.doujinMoe.R;
import com.fanhl.doujinMoe.api.BookApi;
import com.fanhl.doujinMoe.api.PageApi;
import com.fanhl.doujinMoe.api.common.DouJinMoeUrl;
import com.fanhl.doujinMoe.model.Book;
import com.fanhl.doujinMoe.ui.adapter.PageListRecyclerAdapter;
import com.fanhl.doujinMoe.ui.common.AbsActivity;
import com.fanhl.util.GsonUtil;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class DetailsActivity extends AbsActivity {
    public static final String TAG = DetailsActivity.class.getSimpleName();

    public static final String EXTRA_BOOK_DATA = "EXTRA_BOOK_DATA";

    @Bind(R.id.app_bar)
    AppBarLayout            mAppBar;
    @Bind(R.id.toolbar_layout)
    CollapsingToolbarLayout mToolbarLayout;
    @Bind(R.id.preview)
    ImageView               mPreview;
    @Bind(R.id.toolbar)
    Toolbar                 toolbar;
    @Bind(R.id.fab)
    FloatingActionButton    fab;
    @Bind(R.id.recycler_view)
    RecyclerView            mRecyclerView;
    @Bind(R.id.swipe_refresh_layout)
    SwipeRefreshLayout      mSwipeRefreshLayout;

    private MenuItem downloadItem;

    //custom

    private   Book                    book;
    protected PageListRecyclerAdapter mAdapter;

    /*初始数据已刷新*/
    boolean dataRefreshed = false;


    public static void launch(Activity activity, Book book) {
        Intent intent = new Intent(activity, DetailsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(EXTRA_BOOK_DATA, GsonUtil.json(book));
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        //取得数据后再能点详细
        fab.setEnabled(false);
        fab.setOnClickListener(view -> GalleryActivity.launch(DetailsActivity.this, book));

        Intent intent = getIntent();
        book = new Gson().fromJson(intent.getStringExtra(EXTRA_BOOK_DATA), Book.class);
        book = BookApi.getBookFormJson(this, book);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Picasso.with(this)
                .load(DouJinMoeUrl.previewUrl(book.token))
                .into(mPreview);

        setTitle(book.name);

        mAppBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> mSwipeRefreshLayout.setEnabled(verticalOffset == 0));

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getIntArray(R.array.refresh_array));
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshData);

        //流式布局
        StaggeredGridLayoutManager mLayoutManager = new StaggeredGridLayoutManager(getResources().getInteger(R.integer.span_count_page), StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setHasFixedSize(true);


        //mRecyclerView
        mAdapter = new PageListRecyclerAdapter(this, mRecyclerView, book);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setOnItemClickListener((position, viewHolder) -> {
            book.position = position;
            GalleryActivity.launch(DetailsActivity.this, book);
        });

        refreshData();
    }


    @Override
    protected void onResume() {
        super.onResume();
        app.getDownloadManager().registerOnDownloadManagerInteractionListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        app.getDownloadManager().unregisterOnDownloadManagerInteractionListener(this);
    }

    private void refreshData() {
        if (!mSwipeRefreshLayout.isRefreshing()) mSwipeRefreshLayout.setRefreshing(true);
        Observable.<Void>create(subscriber -> {
            try {
                if (book.isDownloaded()) {
                    Log.d(TAG, "书籍已下载:" + book.name);
                    subscriber.onNext(null);
                } else {
                    subscriber.onNext(PageApi.pages(book));
                }
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(aVoid -> {
                    mSwipeRefreshLayout.setRefreshing(false);
                    fab.setEnabled(true);
                    dataRefreshed = true;
                    refreshDownloadItem();
                    mAdapter.notifyDataSetChanged();
                }, throwable -> {
                    mSwipeRefreshLayout.setRefreshing(false);
                    Log.e(TAG, Log.getStackTraceString(throwable));
                    Snackbar.make(mSwipeRefreshLayout, R.string.refresh_fail, Snackbar.LENGTH_LONG).setAction(R.string.action_retry, v -> refreshData()).show();
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_details, menu);

        downloadItem = menu.findItem(R.id.action_download);
        refreshDownloadItem();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_download) {
            item.setEnabled(false);
            download(book);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshDownloadItem() {
        if (downloadItem == null) {
            return;
        }

        if (book.isDownloaded()) {
            downloadItem.setIcon(R.drawable.fa_download_done);
            downloadItem.setEnabled(false);
            return;
        }

        //初次加载数据
        if (!dataRefreshed) {
            downloadItem.setEnabled(false);
            return;
        }

        // FIXME: 15/11/20 把这块分成 将要下载 和 正在下载中 ,并对应不同的 actionView
        if (app.getDownloadManager().isAccepted(book)) {
            downloadItem.setEnabled(false);
            return;
        }

        downloadItem.setEnabled(true);
    }

    private void download(Book book) {
        //用不用先在 snakeBar上确认一下是否下载呢.

        Log.d(TAG, "新增要下载的书籍:" + book.name);
        app.getDownloadManager().accept(book);
    }

    @Override
    public void onDMDownloadSuccess(Book book) {
        Snackbar.make(mRecyclerView, String.format(getString(R.string.download_book_success), book.name), Snackbar.LENGTH_LONG).setAction(R.string.action_check, v -> {
            // FIXME: 15/11/20 跳转到下载列表页面.
        }).show();

        if (this.book.name.equals(book.name)) {
            book.status = Book.Status.DOWNLOADED;
        }
    }

    @Override
    public void onDMDownloadFail(Book book) {
        Snackbar.make(mRecyclerView, String.format(getString(R.string.download_book_fail), book.name), Snackbar.LENGTH_LONG).show();

        if (this.book.name.equals(book.name)) {
            refreshDownloadItem();
        }
    }
}
