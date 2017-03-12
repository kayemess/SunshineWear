package com.example.android.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.wearable.DataMap.TAG;

/**
 * Created by kristenwoodward on 3/11/17.
 */

public class WeatherWearableListenerService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener {

    private static final String TAG = "WearableDataLayer";
    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

    public static final String WEATHER_DATA = "/weather";
    public static final String MAX_TEMP = "max-temp";

    GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i("ListenerService", "OnDataChaged triggered");

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent dataEvent : dataEvents) {

            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                if (dataItem.getUri().getPath().equals("/wearable")) {
                    DataItem item = dataEvent.getDataItem();

                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    String maxTemp = dataMap.getString("max");

                    Intent weatherChanged = new Intent("ACTION_WEATHER_CHANGED");
                    weatherChanged.putExtra("max", maxTemp);

                    sendBroadcast(weatherChanged);
                }
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
