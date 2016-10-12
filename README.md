
![HyperADx Logo](http://d2n7xvwjxl8766.cloudfront.net/assets/site/logo-e04518160888e1f8b3795f0ce01e1909.png) 
&& 
![MoPub Logo](https://5018-presscdn-27-91-pagely.netdna-ssl.com/wp-content/themes/mopub/img/logo.png)


* [Download](https://github.com/hyperads/android-MoPub-adapter/releases) and extract the Mopub adapter if needed.

You can use Hyperadx as a Network in Mopub's Mediation platform.
Setup SDKs

* [Integrate](https://github.com/mopub/mopub-android-sdk/wiki/Getting-Started) with Mopub SDK
* Install Hyperadx SDK

Setup Mopub Dashboard

* Create an "Hyperadx" Network in Mopub's dashboard and connect it to your Ad Units.

<img src="/docs/images/1.png" title="sample" width="500" height="460" />

* In Mopub's dashboard select Networks > Add New network

<img src="/docs/images/2.png" title="sample" width="500" height="460" />

Then select Custom Native Network. Complete the fields accordingly to the Ad Unit that you want to use

### Native

* Custom Event Class: `com.mopub.nativeads.HyperadxNativeMopub`
* Custom Event Class Data: `{"PLACEMENT":"<YOUR PLACEMENT>"}`

<img src="/docs/images/3.png" title="sample" width="500" height="460" />

**You can use the test placement "5b3QbMRQ"**

> Add adapter in your project. Create package "com.mopub.nativeads" in your project and put this class in there:

```java
HyperadxNativeMopub.java:

package com.mopub.nativeads;
import android.app.Activity;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HyperadxNativeMopub extends CustomEventNative {
    private static final String PLACEMENT_KEY = "PLACEMENT";
    com.hyperadx.lib.sdk.nativeads.HADNativeAd nativeAd;

    @Override
    protected void loadNativeAd(final @NonNull Activity activity, final @NonNull CustomEventNativeListener customEventNativeListener, @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) {
        final String placement;
        if ((serverExtras != null) && serverExtras.containsKey(PLACEMENT_KEY)) {
            placement = serverExtras.get(PLACEMENT_KEY);
 } else {
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        nativeAd = new com.hyperadx.lib.sdk.nativeads.HADNativeAd(activity, placement); //Native AD constructor
        nativeAd.setContent("title,icon,description");
        nativeAd.setAdListener(new AdListener() { // Add Listeners
            @Override
            public void onAdLoaded(Ad ad) {
                customEventNativeListener.onNativeAdLoaded(new HyperadxNativeAd(ad, nativeAd, activity));

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


    class HyperadxNativeAd extends StaticNativeAd {
        final Ad hadModel;
        final com.hyperadx.lib.sdk.nativeads.HADNativeAd nativeAd;
        final ImpressionTracker impressionTracker;
        final NativeClickHandler nativeClickHandler;
        final Activity activity;
        public HyperadxNativeAd(@NonNull Ad customModel, HADNativeAd nativeAd, Activity activity) {

            hadModel = customModel;
            this.nativeAd = nativeAd;
            this.activity = activity;
            impressionTracker = new ImpressionTracker(activity);
            nativeClickHandler = new NativeClickHandler(activity);
            setIconImageUrl(hadModel.getIcon_url());
            setMainImageUrl(hadModel.getImage_url());
            setTitle(hadModel.getTitle());
            setText(hadModel.getDescription());
            setClickDestinationUrl(hadModel.getClickUrl());
            for (Ad.Tracker tracker : hadModel.getTrackers())
                if (tracker.getType().equals("impression")) {
                    addImpressionTracker(tracker.getUrl());
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
                userAgent = com.hyperadx.lib.sdk.Util.getDefaultUserAgentString(activity);

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

            @Override
            protected void onPostExecute(String url) {
            }
        }
    }
}

```


### Interstitial

Custom Event Class: `com.mopub.mobileads.HyperadxInterstitialMopub`

Custom Event Class Data: `{"PLACEMENT":"<YOUR PLACEMENT>"}`

**You can use the test placement `5b3QbMRQ`**

> Add adapter in your project
Create package "com.mopub.mobileads" in your project and put this class in there:

```java
HyperadxInterstitialMopub.java:

package com.mopub.mobileads;
import android.content.Context;
import android.util.Log;
import com.hyperadx.lib.sdk.interstitialads.HADInterstitialAd;
import com.hyperadx.lib.sdk.interstitialads.InterstitialAdListener;
import java.util.Map;

public class HyperadxInterstitialMopub extends CustomEventInterstitial {
    private static final String PLACEMENT_KEY = "PLACEMENT";
    private HADInterstitialAd interstitialAd;
    private com.hyperadx.lib.sdk.interstitialads.Ad iAd = null;
    CustomEventInterstitialListener customEventInterstitialListener;

    @Override
    protected void loadInterstitial(final Context context, final CustomEventInterstitialListener customEventInterstitialListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {
        final String placement;
        final String appId;
        if (serverExtras != null && serverExtras.containsKey(PLACEMENT_KEY)) {
            placement = serverExtras.get(PLACEMENT_KEY);

 } else {
            customEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        interstitialAd = new HADInterstitialAd(context, placement); //Interstitial AD constructor
        interstitialAd.setAdListener(new InterstitialAdListener() { // Set Listener
            @Override
            public void onAdLoaded(com.hyperadx.lib.sdk.interstitialads.Ad ad) { // Called when AD is Loaded
                iAd = ad;
                //   Toast.makeText(context, "Interstitial Ad loaded", Toast.LENGTH_SHORT).show();
                customEventInterstitialListener.onInterstitialLoaded();
            }

            @Override
            public void onError(com.hyperadx.lib.sdk.interstitialads.Ad Ad, String error) { // Called when load is fail
                //   Toast.makeText(context, "Interstitial Ad failed to load with error: " + error, Toast.LENGTH_SHORT).show();
                customEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.UNSPECIFIED);

            @Override
            public void onInterstitialDisplayed() { // Called when Ad was impressed
                //    Toast.makeText(context, "Tracked Interstitial Ad impression", Toast.LENGTH_SHORT).show();
                customEventInterstitialListener.onInterstitialShown();
            }

            @Override
            public void onInterstitialDismissed(com.hyperadx.lib.sdk.interstitialads.Ad ad) { // Called when Ad was dissnissed by user
                //   Toast.makeText(context, "Interstitial Ad Dismissed", Toast.LENGTH_SHORT).show();
                customEventInterstitialListener.onInterstitialDismissed();

            @Override
            public void onAdClicked() { // Called when user click on AD
                //   Toast.makeText(context, "Tracked Interstitial Ad click", Toast.LENGTH_SHORT).show();
                customEventInterstitialListener.onInterstitialClicked();
            }
        });

        this.customEventInterstitialListener = customEventInterstitialListener;
        interstitialAd.loadAd(); // Call to load AD

    @Override
    protected void showInterstitial() {
        if (iAd != null)
            HADInterstitialAd.show(iAd); // Call to show AD
        else
            Log.e("HADInterstitialMopub", "The Interstitial AD not ready yet. Try again!");
    }

    @Override
    protected void onInvalidate() {
    }
}
```

> This is your adapter. Now you can use Mopub as usual.