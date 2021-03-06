/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.example.android.app.R;
import com.example.android.app.Utility;
import com.example.android.app.WeatherWearableListenerService;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.WearableListenerService;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private final static String TAG = "SunshineWatchFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private String mHighTemp;
    private String mLowTemp;

    private int mWeatherId;


    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherUpdateReceiver = false;

        Paint mBackgroundPaint;

        Paint mHoursPaint;
        Paint mMinutesPaint;
        Paint mColonPaint;

        Paint mDatePaint;

        Paint mLinePaint;

        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Paint mWeatherIconPaint;

        boolean mAmbient;
        Calendar mCalendar;

        SimpleDateFormat mSimpleDateFormat;


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        final BroadcastReceiver mWeatherChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i("watchface", "onReceive called");

                mHighTemp = intent.getStringExtra("max");
                mLowTemp = intent.getStringExtra("min");
                mWeatherId = intent.getIntExtra("weather_id", -1);
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        Resources mResources;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            WeatherWearableListenerService weatherListenerService = new WeatherWearableListenerService();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(false)
                    .build());
            mResources = SunshineWatchFace.this.getResources();

            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mResources.getColor(R.color.background));
        }

        private void initializeWatchFace() {
            mYOffset = mResources.getDimension(R.dimen.digital_y_offset);
            mHoursPaint = createTextPaint(mResources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mMinutesPaint = createTextPaint(mResources.getColor(R.color.digital_text));
            mColonPaint = createTextPaint(mResources.getColor(R.color.digital_text));

            mDatePaint = createTextPaint(mResources.getColor(R.color.colorPrimaryLight));

            mHighTempPaint = createTextPaint(mResources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mLowTempPaint = createTextPaint(mResources.getColor(R.color.colorPrimaryLight));

            mLinePaint = createTextPaint(mResources.getColor(R.color.colorPrimaryLight));

            mCalendar = Calendar.getInstance();

            mWeatherIconPaint = new Paint();

            mSimpleDateFormat = new SimpleDateFormat("EEE, MMM d yyyy");
            mSimpleDateFormat.setTimeZone(mCalendar.getTimeZone());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeZoneReceiver();
                registerWeatherUpdateReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterTimeZoneReceiver();
                unregisterWeatherUpdateReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void registerWeatherUpdateReceiver() {
            if (mRegisteredWeatherUpdateReceiver) {
                return;
            }
            mRegisteredWeatherUpdateReceiver = true;
            IntentFilter filter = new IntentFilter("ACTION_WEATHER_CHANGED");
            SunshineWatchFace.this.registerReceiver(mWeatherChangedReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void unregisterWeatherUpdateReceiver() {
            if (!mRegisteredWeatherUpdateReceiver) {
                return;
            }
            mRegisteredWeatherUpdateReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mWeatherChangedReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            //mTextPaint.setTextSize(textSize);

            mHoursPaint.setTextSize(textSize);
            mMinutesPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mDatePaint.setTextSize(22);

            mHighTempPaint.setTextSize(30);
            mLowTempPaint.setTextSize(30);

            mLinePaint.setStrokeWidth(0);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHoursPaint.setAntiAlias(!inAmbientMode);
                    mMinutesPaint.setAntiAlias(!inAmbientMode);
                    mColonPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLinePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            float x = mXOffset;
            float y = mYOffset;
            float xTextWidth;
            float xCanvasWidth;

            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            String colonString = ":";
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));

            xTextWidth = mHoursPaint.measureText(hourString) +
                    mColonPaint.measureText(colonString) +
                    mMinutesPaint.measureText(minuteString);

            xCanvasWidth = bounds.width();
            x = (xCanvasWidth - xTextWidth) / 2;

            canvas.drawText(hourString, x, y, mHoursPaint);
            x += mHoursPaint.measureText(hourString);

            canvas.drawText(colonString, x, y, mColonPaint);
            x += mColonPaint.measureText(colonString);

            canvas.drawText(minuteString, x, y, mMinutesPaint);

            y += 27;

            String dateString = mSimpleDateFormat.format(mCalendar.getTime());
            String dateUpper = dateString.toUpperCase();

            xTextWidth = mDatePaint.measureText((dateUpper));
            x = (xCanvasWidth - xTextWidth) / 2;

            canvas.drawText(dateUpper, x, y, mDatePaint);

            y += 15;

            x = bounds.centerX();

            canvas.drawLine(x - 40, y, x + 40, y, mLinePaint);

            y += 50;

            float yBitmap = y - 40;

            if (mHighTemp != null && mLowTemp != null) {
                if (mWeatherId < 0)
                    mWeatherId = 800;

                Bitmap weatherArt = BitmapFactory.decodeResource(getResources(), Utility.getArtResourceForWeatherCondition(mWeatherId));
                int scale = 60;
                Bitmap weatherScaled = Bitmap.createScaledBitmap(weatherArt, scale, scale, true);

                float xWeatherWidth = scale + 15 + mHighTempPaint.measureText(mHighTemp) + 10 + mLowTempPaint.measureText(mLowTemp);
                x = (xCanvasWidth - xWeatherWidth) / 2;

                canvas.drawBitmap(weatherScaled, x, yBitmap, mWeatherIconPaint);

                x += scale + 10;

                canvas.drawText(mHighTemp, x, y, mHighTempPaint);

                x += mHighTempPaint.measureText(mHighTemp) + 5;

                canvas.drawText(mLowTemp, x, y, mLowTempPaint);


            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
