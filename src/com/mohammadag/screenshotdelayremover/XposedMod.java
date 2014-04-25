package com.mohammadag.screenshotdelayremover;

import android.os.Handler;
import android.os.SystemClock;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {

	/* We might be configurable later on, but not now, no */
	private static final long DELAY = 0L;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("android"))
			return;

		/* 
		 * Android 4.2 introduced the method getScreenshotChordLongPressDelay to double
		 * the delay on the lockscreen, no idea why, probably for devices with hardware
		 * keys that can be easily pressed in a pocket.
		 */
		try {
			XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager",
					lpparam.classLoader, "getScreenshotChordLongPressDelay", XC_MethodReplacement.returnConstant(DELAY));
		} catch (Throwable t) {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
				XposedBridge.log("Failed to hook PhoneWindowManager, screenshot delays are here to stay: " + t.getMessage());
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
			return;

		XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager",
				lpparam.classLoader, "getScreenshotChordLongPressDelay", new XC_MethodReplacement() {
			@Override
			protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
				boolean mScreenshotChordEnabled = XposedHelpers.getBooleanField(param.thisObject,
						"mScreenshotChordEnabled");
				boolean mVolumeDownKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
						"mVolumeDownKeyTriggered");
				boolean mPowerKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
						"mPowerKeyTriggered");
				boolean mVolumeUpKeyTriggered = XposedHelpers.getBooleanField(param.thisObject,
						"mVolumeUpKeyTriggered");
				long mVolumeDownKeyTime = XposedHelpers.getLongField(param.thisObject,
						"mVolumeDownKeyTime");
				long mPowerKeyTime = XposedHelpers.getLongField(param.thisObject,
						"mPowerKeyTime");
				long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = XposedHelpers.getLongField(param.thisObject,
						"SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS");

				if (mScreenshotChordEnabled
						&& mVolumeDownKeyTriggered && mPowerKeyTriggered && !mVolumeUpKeyTriggered) {
					final long now = SystemClock.uptimeMillis();
					if (now <= mVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
							&& now <= mPowerKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
						XposedHelpers.setBooleanField(param.thisObject, "mVolumeDownKeyConsumedByScreenshotChord", true);
						XposedHelpers.callMethod(param.thisObject, "cancelPendingPowerKeyAction");

						Handler mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
						Runnable mScreenshotChordLongPress = (Runnable) XposedHelpers.getObjectField(param.thisObject,
								"mScreenshotChordLongPress");
						mHandler.postDelayed(mScreenshotChordLongPress, DELAY);
					}
				}
				return null;
			}
		});

	}

}
