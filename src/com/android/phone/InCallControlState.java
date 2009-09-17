/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;


/**
 * Helper class to keep track of enabledness, visibility, and "on/off"
 * or "checked" state of the various controls available in the in-call
 * UI, based on the current telephony state.
 *
 * This class is independent of the exact UI controls used on any given
 * device.  (Some devices use onscreen touchable buttons, for example, and
 * other devices use menu items.)  To avoid cluttering up the InCallMenu
 * and InCallTouchUi code with logic about which functions are available
 * right now, we instead have that logic here, and provide simple boolean
 * flags to indicate the state and/or enabledness of all possible in-call
 * user operations.
 *
 * (In other words, this is the "model" that corresponds to the "view"
 * implemented by InCallMenu and InCallTouchUi.)
 */
public class InCallControlState {
    private static final String LOG_TAG = "InCallControlState";
    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    private InCallScreen mInCallScreen;
    private Phone mPhone;

    //
    // Our "public API": Boolean flags to indicate the state and/or
    // enabledness of all possible in-call user operations:
    //

    public boolean manageConferenceVisible;
    public boolean manageConferenceEnabled;
    //
    public boolean canAddCall;
    //
    public boolean canSwap;
    public boolean canMerge;
    //
    public boolean bluetoothEnabled;
    public boolean bluetoothIndicatorOn;
    //
    public boolean speakerEnabled;
    public boolean speakerOn;
    //
    public boolean canMute;
    public boolean muteIndicatorOn;
    //
    public boolean dialpadEnabled;
    public boolean dialpadVisible;
    //
    /** True if the "Hold" function is *ever* available on this device */
    public boolean supportsHold;
    /** True if the call is currently on hold */
    public boolean onHold;
    /** True if the "Hold" function should be available right now */
    public boolean canHold;


    public InCallControlState(InCallScreen inCallScreen, Phone phone) {
        if (DBG) log("InCallControlState constructor...");
        mInCallScreen = inCallScreen;
        mPhone = phone;
    }

    /**
     * Updates all our public boolean flags based on the current state of
     * the Phone.
     */
    public void update() {
        final boolean hasRingingCall = !mPhone.getRingingCall().isIdle();
        final Call fgCall = mPhone.getForegroundCall();
        final Call.State fgCallState = fgCall.getState();
        final boolean hasActiveCall = !fgCall.isIdle();
        final boolean hasHoldingCall = !mPhone.getBackgroundCall().isIdle();

        // Manage conference:
        if (mPhone.getPhoneName().equals("GSM")) {
            // This item is visible only if the foreground call is a
            // conference call, and it's enabled unless the "Manage
            // conference" UI is already up.
            manageConferenceVisible = PhoneUtils.isConferenceCall(fgCall);
            manageConferenceEnabled =
                    manageConferenceVisible && !mInCallScreen.isManageConferenceMode();
        } else {
            // CDMA has no concept of managing a conference call.
            manageConferenceVisible = false;
            manageConferenceEnabled = false;
        }

        // "Add call":
        canAddCall = PhoneUtils.okToAddCall(mPhone);

        // Swap / merge calls
        canSwap = PhoneUtils.okToSwapCalls(mPhone);
        canMerge = PhoneUtils.okToMergeCalls(mPhone);

        // "Bluetooth":
        if (mInCallScreen.isBluetoothAvailable()) {
            bluetoothEnabled = true;
            bluetoothIndicatorOn = mInCallScreen.isBluetoothAudioConnectedOrPending();
        } else {
            bluetoothEnabled = false;
            bluetoothIndicatorOn = false;
        }

        // "Speaker": always enabled.
        // The current speaker state comes from the AudioManager.
        speakerEnabled = true;
        speakerOn = PhoneUtils.isSpeakerOn(mInCallScreen);

        // "Mute": only enabled when the foreground call is ACTIVE.
        // (It's meaningless while on hold, or while DIALING/ALERTING.)
        // Also disabled (on CDMA devices) during emergency calls.
        if (mPhone.getPhoneName().equals("CDMA")) {
            Connection c = fgCall.getLatestConnection();
            boolean isEmergencyCall = false;
            if (c != null) isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(c.getAddress());

            if (isEmergencyCall) { // disable "Mute" item
                canMute = false;
                muteIndicatorOn = false;
            } else {
                canMute = (fgCallState == Call.State.ACTIVE);
                muteIndicatorOn = PhoneUtils.getMute(mPhone);
            }
        } else {
            canMute = (fgCallState == Call.State.ACTIVE);
            muteIndicatorOn = PhoneUtils.getMute(mPhone);
        }

        // "Dialpad": Enabled only when it's OK to use the dialpad in the
        // first place.
        dialpadEnabled = mInCallScreen.okToShowDialpad();

        // Also keep track of whether the dialpad is currently "opened"
        // (i.e. visible).
        dialpadVisible = mInCallScreen.isDialerOpened();

        // "Hold:
        if (mPhone.getPhoneName().equals("GSM")) {
            // "On hold" means that there's a holding call and
            // *no* foreground call.  (If there *is* a foreground call,
            // that's "two lines in use".)  "Hold" is disabled if both
            // lines are in use, or if the foreground call is non-idle and
            // in any state other than ACTIVE.
            supportsHold = true;
            onHold = hasHoldingCall && !hasActiveCall;
            canHold = !((hasActiveCall && hasHoldingCall)
                        || (hasActiveCall && (fgCallState != Call.State.ACTIVE)));
        } else {
            // CDMA has no concept of "putting a call on hold."
            supportsHold = false;
            onHold = false;
            canHold = false;
        }

        if (DBG) dumpState();
    }

    public void dumpState() {
        log("InCallControlState:");
        log("  manageConferenceVisible: " + manageConferenceVisible);
        log("  manageConferenceEnabled: " + manageConferenceEnabled);
        log("  canAddCall: " + canAddCall);
        log("  canSwap: " + canSwap);
        log("  canMerge: " + canMerge);
        log("  bluetoothEnabled: " + bluetoothEnabled);
        log("  bluetoothIndicatorOn: " + bluetoothIndicatorOn);
        log("  speakerEnabled: " + speakerEnabled);
        log("  speakerOn: " + speakerOn);
        log("  canMute: " + canMute);
        log("  muteIndicatorOn: " + muteIndicatorOn);
        log("  dialpadEnabled: " + dialpadEnabled);
        log("  dialpadVisible: " + dialpadVisible);
        log("  onHold: " + onHold);
        log("  canHold: " + canHold);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
