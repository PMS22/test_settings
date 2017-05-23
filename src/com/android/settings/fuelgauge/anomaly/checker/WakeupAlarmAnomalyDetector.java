/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.fuelgauge.anomaly.checker;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryStats;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;
import android.text.format.DateUtils;
import android.util.ArrayMap;

import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.Utils;
import com.android.settings.fuelgauge.BatteryUtils;
import com.android.settings.fuelgauge.anomaly.Anomaly;
import com.android.settings.fuelgauge.anomaly.AnomalyDetectionPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Check whether apps has too many wakeup alarms
 */
public class WakeupAlarmAnomalyDetector implements AnomalyDetector {
    private static final String TAG = "WakeupAlarmAnomalyDetector";
    //TODO: add this threshold into AnomalyDetectionPolicy
    private static final int WAKEUP_ALARM_THRESHOLD = 60;
    private Context mContext;
    @VisibleForTesting
    BatteryUtils mBatteryUtils;

    public WakeupAlarmAnomalyDetector(Context context) {
        mContext = context;
        mBatteryUtils = BatteryUtils.getInstance(context);
    }

    @Override
    public List<Anomaly> detectAnomalies(BatteryStatsHelper batteryStatsHelper) {
        final List<BatterySipper> batterySippers = batteryStatsHelper.getUsageList();
        final List<Anomaly> anomalies = new ArrayList<>();
        final long totalRunningHours = mBatteryUtils.calculateRunningTimeBasedOnStatsType(
                batteryStatsHelper, BatteryStats.STATS_SINCE_CHARGED) / DateUtils.HOUR_IN_MILLIS;

        if (totalRunningHours != 0) {
            for (int i = 0, size = batterySippers.size(); i < size; i++) {
                final BatterySipper sipper = batterySippers.get(i);
                final BatteryStats.Uid uid = sipper.uidObj;
                if (uid == null || mBatteryUtils.shouldHideSipper(sipper)) {
                    continue;
                }

                final int wakeups = getWakeupAlarmCountFromUid(uid);
                if ((wakeups / totalRunningHours) > WAKEUP_ALARM_THRESHOLD) {
                    final String packageName = mBatteryUtils.getPackageName(uid.getUid());
                    final CharSequence displayName = Utils.getApplicationLabel(mContext,
                            packageName);

                    Anomaly anomaly = new Anomaly.Builder()
                            .setUid(uid.getUid())
                            .setType(Anomaly.AnomalyType.WAKEUP_ALARM)
                            .setDisplayName(displayName)
                            .setPackageName(packageName)
                            .build();
                    anomalies.add(anomaly);
                }
            }
        }

        return anomalies;
    }

    @VisibleForTesting
    int getWakeupAlarmCountFromUid(BatteryStats.Uid uid) {
        int wakeups = 0;
        final ArrayMap<String, ? extends BatteryStats.Uid.Pkg> packageStats
                = uid.getPackageStats();
        for (int ipkg = packageStats.size() - 1; ipkg >= 0; ipkg--) {
            final BatteryStats.Uid.Pkg ps = packageStats.valueAt(ipkg);
            final ArrayMap<String, ? extends BatteryStats.Counter> alarms =
                    ps.getWakeupAlarmStats();
            for (int iwa = alarms.size() - 1; iwa >= 0; iwa--) {
                int count = alarms.valueAt(iwa).getCountLocked(BatteryStats.STATS_SINCE_CHARGED);
                wakeups += count;
            }

        }

        return wakeups;
    }

}
