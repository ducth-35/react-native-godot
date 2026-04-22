/**************************************************************************/
/*  RTNGodotView.java                                                     */
/**************************************************************************/
/* Copyright (c) 2024-2025 Slay GmbH                                      */
/*                                                                        */
/* Permission is hereby granted, free of charge, to any person obtaining  */
/* a copy of this software and associated documentation files (the        */
/* "Software"), to deal in the Software without restriction, including    */
/* without limitation the rights to use, copy, modify, merge, publish,    */
/* distribute, sublicense, and/or sell copies of the Software, and to     */
/* permit persons to whom the Software is furnished to do so, subject to  */
/* the following conditions:                                              */
/*                                                                        */
/* The above copyright notice and this permission notice shall be         */
/* included in all copies or substantial portions of the Software.        */
/*                                                                        */
/* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,        */
/* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF     */
/* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. */
/* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY   */
/* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,   */
/* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE      */
/* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                 */
/**************************************************************************/

package com.rtngodot;

import org.godotengine.godot.input.GodotInputHandler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RTNGodotView extends TextureView implements TextureView.SurfaceTextureListener {
	private static final String TAG = "RTNGodotView";

	private String windowName = "";

	private GodotInputHandler mInputHandler;

	/** Surface wrapping the SurfaceTexture provided by TextureView. */
	private Surface mSurface;

	/**
	 * Guards against double-cleanup: both onSurfaceTextureDestroyed and
	 * onDetachedFromWindow can fire during React Native unmount. Without this
	 * flag, the cleanup would be called twice which could crash.
	 */
	private boolean mCleanedUp = false;

	public RTNGodotView(Context context) {
		super(context);
		this.configureComponent();
	}

	public RTNGodotView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		this.configureComponent();
	}

	public RTNGodotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		this.configureComponent();
	}

	private void configureComponent() {
		mInputHandler = RTNLibGodot.getInstance().getInputHandler();
		setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		// TextureView renders into the normal view hierarchy – no separate window layer.
		// setOpaque(true) avoids unnecessary alpha blending overhead.
		setOpaque(true);
		setSurfaceTextureListener(this);
	}

	private GodotInputHandler getInputHandler() {
		if (mInputHandler == null) {
			mInputHandler = RTNLibGodot.getInstance().getInputHandler();
		}
		return mInputHandler;
	}

	public void setWindowName(String newWindowName) {
		windowName = newWindowName;
	}

	public String getWindowName() {
		return windowName;
	}

	/**
	 * Shared cleanup: tells RTNLibGodot to revert the main window to the
	 * internal surface (or remove a sub-window), then releases the Surface.
	 * Safe to call multiple times (idempotent via mCleanedUp flag).
	 */
	private void cleanupSurface(String caller) {
		if (mCleanedUp) {
			Log.d(TAG, String.format("%s: already cleaned up for %s, skipping", caller, windowName));
			return;
		}
		mCleanedUp = true;
		Log.i(TAG, String.format("%s: cleaning up surface for %s", caller, windowName));
		RTNLibGodot.getInstance().removeWindow(windowName);
		if (mSurface != null) {
			mSurface.release();
			mSurface = null;
		}
	}

	// ---- TextureView.SurfaceTextureListener ----

	@Override
	public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
		Log.i(TAG, String.format("onSurfaceTextureAvailable: %s %dx%d", windowName, width, height));
		mSurface = new Surface(surfaceTexture);
		mCleanedUp = false;
		RTNLibGodot.getInstance().updateWindow(windowName, mSurface, width, height);
	}

	@Override
	public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
		Log.i(TAG, String.format("onSurfaceTextureSizeChanged: %s %dx%d", windowName, width, height));
		if (mSurface != null && !mCleanedUp) {
			RTNLibGodot.getInstance().updateWindow(windowName, mSurface, width, height);
		}
	}

	@Override
	public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
		Log.i(TAG, String.format("onSurfaceTextureDestroyed: %s", windowName));
		cleanupSurface("onSurfaceTextureDestroyed");
		// Return true to let the framework release the SurfaceTexture.
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
		// No-op: called on every frame update from the producer side.
	}

	// ---- Cleanup on detach (e.g. React Native unmount) ----

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		cleanupSurface("onDetachedFromWindow");
	}

	// ---- Touch / pointer input (unchanged) ----

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		GodotInputHandler handler = getInputHandler();
		if (handler == null) return false;
		return handler.onTouchEvent(event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		GodotInputHandler handler = getInputHandler();
		if (handler == null) return false;
		return handler.onGenericMotionEvent(event);
	}

	@Override
	public boolean onCapturedPointerEvent(MotionEvent event) {
		GodotInputHandler handler = getInputHandler();
		if (handler == null) return false;
		return handler.onGenericMotionEvent(event);
	}

	private boolean canCapturePointer() {
		return true;
	}

	@Override
	public void requestPointerCapture() {
		if (canCapturePointer()) {
			super.requestPointerCapture();
			GodotInputHandler handler = getInputHandler();
			if (handler != null) handler.onPointerCaptureChange(true);
		}
	}

	@Override
	public void releasePointerCapture() {
		super.releasePointerCapture();
		GodotInputHandler handler = getInputHandler();
		if (handler != null) handler.onPointerCaptureChange(false);
	}

	@Override
	public void onPointerCaptureChange(boolean hasCapture) {
		super.onPointerCaptureChange(hasCapture);
		GodotInputHandler handler = getInputHandler();
		if (handler != null) handler.onPointerCaptureChange(hasCapture);
	}
}