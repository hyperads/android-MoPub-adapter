package com.mopub.nativeads;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;

import com.hyperadx.lib.sdk.nativeads.Ad;
import com.hyperadx.lib.sdk.nativeads.AdListener;
import com.hyperadx.lib.sdk.nativeads.HADNativeAd;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mopub.nativeads.NativeImageHelper.preCacheImages;


public class HyperadxNativeMopub extends CustomEventNative {

    private static final String PLACEMENT_KEY = "PLACEMENT";


    com.hyperadx.lib.sdk.nativeads.HADNativeAd nativeAd;

    @Override
    protected void loadNativeAd(@NonNull final Context context, @NonNull final CustomEventNativeListener customEventNativeListener, @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) {


        final String placement;
        if ((serverExtras != null) && serverExtras.containsKey(PLACEMENT_KEY)) {
            placement = serverExtras.get(PLACEMENT_KEY);
        } else {
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        nativeAd = new com.hyperadx.lib.sdk.nativeads.HADNativeAd(context, placement); //Native AD constructor
        nativeAd.setContent("title,icon,description");

        nativeAd.setAdListener(new AdListener() { // Add Listeners

            @Override
            public void onAdLoaded(final Ad ad) {

                //   customEventNativeListener.onNativeAdLoaded(new HyperadxNativeAd(ad, nativeAd, activity));

                List<String> imageUrls = new ArrayList<String>();

                if (isValidURL(ad.getImage_url()))
                    imageUrls.add(ad.getImage_url());


                if (isValidURL(ad.getIcon_url()))
                    imageUrls.add(ad.getIcon_url());


                preCacheImages(context, imageUrls, new NativeImageHelper.ImageListener() {
                    @Override
                    public void onImagesCached() {
                        customEventNativeListener.onNativeAdLoaded(new HyperadxNativeAd(ad, nativeAd, context));
                    }

                    @Override
                    public void onImagesFailedToCache(NativeErrorCode errorCode) {
                        customEventNativeListener.onNativeAdFailed(NativeErrorCode.EMPTY_AD_RESPONSE);
                    }
                });

            }

            @Override
            public void onError(Ad nativeAd, String error) { // Called when load is fail

                customEventNativeListener.onNativeAdFailed(NativeErrorCode.EMPTY_AD_RESPONSE);

            }

            @Override
            public void onAdClicked() { // Called when user click on AD
                Log.wtf("TAG", "AD Clicked");
            }
        });

        nativeAd.loadAd();

    }


    class HyperadxNativeAd extends StaticNativeAd {

        final Ad hadModel;
        final com.hyperadx.lib.sdk.nativeads.HADNativeAd nativeAd;
        final ImpressionTracker impressionTracker;
        final NativeClickHandler nativeClickHandler;
        final Context context;

        public HyperadxNativeAd(@NonNull Ad customModel, HADNativeAd nativeAd, Context context) {

            hadModel = customModel;
            this.nativeAd = nativeAd;
            this.context = context;
            impressionTracker = new ImpressionTracker(context);
            nativeClickHandler = new NativeClickHandler(context);

            setIconImageUrl(hadModel.getIcon_url());
            setMainImageUrl(hadModel.getImage_url());

            setTitle(hadModel.getTitle());
            setText(hadModel.getDescription());
            setCallToAction(hadModel.getCta());

            setClickDestinationUrl(hadModel.getClickUrl());

            for (Ad.Tracker tracker : hadModel.getTrackers())
                if (tracker.getType().equals("impression")) {
                    addImpressionTracker(tracker.getUrl());
                }

        }

        @Override
        public void prepare(final View view) {
            impressionTracker.addView(view, this);
            nativeClickHandler.setOnClickListener(view, this);
        }

        @Override
        public void recordImpression(final View view) {
            notifyAdImpressed();
            for (Ad.Tracker tracker : hadModel.getTrackers())
                if (tracker.getType().equals("impression")) {
                    new LoadUrlTask().execute(tracker.getUrl());
                }
        }

        @Override
        public void handleClick(final View view) {
            notifyAdClicked();
            nativeClickHandler.openClickDestinationUrl(getClickDestinationUrl(), view);
            if (hadModel.getClickUrl() != null)
                new LoadUrlTask().execute(hadModel.getClickUrl());
        }

        private class LoadUrlTask extends AsyncTask<String, Void, String> {

            String userAgent;

            public LoadUrlTask() {
                userAgent = com.hyperadx.lib.sdk.Util.getDefaultUserAgentString(context);
            }

            @Override
            protected String doInBackground(String... urls) {
                String loadingUrl = urls[0];
                URL url = null;
                try {
                    url = new URL(loadingUrl);
                } catch (MalformedURLException e) {
                    return (loadingUrl != null) ? loadingUrl : "";
                }
                com.hyperadx.lib.sdk.HADLog.d("Checking URL redirect:" + loadingUrl);

                int statusCode = -1;
                HttpURLConnection connection = null;
                String nextLocation = url.toString();

                Set<String> redirectLocations = new HashSet<String>();
                redirectLocations.add(nextLocation);

                try {
                    do {
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestProperty("User-Agent",
                                userAgent);
                        connection.setInstanceFollowRedirects(false);

                        statusCode = connection.getResponseCode();
                        if (statusCode == HttpURLConnection.HTTP_OK) {
                            connection.disconnect();
                            break;
                        } else {
                            nextLocation = connection.getHeaderField("location");
                            connection.disconnect();
                            if (!redirectLocations.add(nextLocation)) {
                                com.hyperadx.lib.sdk.HADLog.d("URL redirect cycle detected");
                                return "";
                            }

                            url = new URL(nextLocation);
                        }
                    }
                    while (statusCode == HttpURLConnection.HTTP_MOVED_TEMP || statusCode == HttpURLConnection.HTTP_MOVED_PERM
                            || statusCode == HttpURLConnection.HTTP_UNAVAILABLE
                            || statusCode == HttpURLConnection.HTTP_SEE_OTHER);
                } catch (IOException e) {
                    return (nextLocation != null) ? nextLocation : "";
                } finally {
                    if (connection != null)
                        connection.disconnect();
                }

                return nextLocation;
            }

            @Override
            protected void onPostExecute(String url) {

            }
        }

    }

    public boolean isValidURL(String urlStr) {

        if (urlStr == null) return false;

        try {
            URL url = new URL(urlStr);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

}
