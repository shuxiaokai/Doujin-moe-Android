package com.fanhl.doujinMoe.ui.fragment;

import android.support.design.widget.Snackbar;
import android.util.Log;

import com.fanhl.doujinMoe.R;
import com.fanhl.doujinMoe.api.HomeApi;
import com.fanhl.doujinMoe.api.form.NewestForm;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by fanhl on 15/11/8.
 */
public class NewestFragment extends AbsBookRecyclerFragment {
    public static final String TAG = NewestFragment.class.getSimpleName();

    public static NewestFragment newInstance() {
        return new NewestFragment();
    }

    @Override
    protected void refreshData() {
        if (!mSwipeRefreshLayout.isRefreshing()) mSwipeRefreshLayout.setRefreshing(true);
        Observable.<NewestForm>create(subscriber -> {
            try {
                subscriber.onNext(HomeApi.newest(1));
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(newestForm -> {
                    if (mSwipeRefreshLayout == null) return;
                    mSwipeRefreshLayout.setRefreshing(false);
                    mBooks.clear();
                    mBooks.addAll(newestForm.newest);
                    mAdapter.notifyDataSetChanged();
                }, throwable -> {
                    if (mSwipeRefreshLayout == null) return;
                    mSwipeRefreshLayout.setRefreshing(false);
                    Log.e(TAG, Log.getStackTraceString(throwable));
                    Snackbar.make(mSwipeRefreshLayout, R.string.text_newest_get_fail, Snackbar.LENGTH_LONG).setAction(R.string.action_retry, v -> refreshData()).show();
                });
    }
}
