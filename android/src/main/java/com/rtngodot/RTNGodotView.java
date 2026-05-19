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
	private boolean isDestroyed = false;
	/** True once setSurfaceTextureListener(this) has been called at least once. */
	private boolean listenerRegistered = false;

	/** The Surface currently used by Godot for this window. */
	private Surface mCurrentSurface;

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
		setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		// TextureView must be non-opaque to sit correctly inside React Native hierarchy.
		// Hardware acceleration is required for TextureView to render.
		setOpaque(false);
		setLayerType(LAYER_TYPE_HARDWARE, null);
		// DO NOT call setSurfaceTextureListener here.
		// It is deferred to setWindowName() / onAttachedToWindow() so that
		// windowName is guaranteed to be set before onSurfaceTextureAvailable fires.
	}

	private GodotInputHandler getInputHandler() {
		if (mInputHandler == null) {
			mInputHandler = RTNLibGodot.getInstance().getInputHandler();
		}
		return mInputHandler;
	}

	public void setWindowName(String newWindowName) {
		windowName = newWindowName != null ? newWindowName : "";
		// Register the listener now that windowName is finalised.
		// If the SurfaceTexture is already available, Android fires
		// onSurfaceTextureAvailable immediately upon setSurfaceTextureListener.
		if (!isDestroyed) {
			listenerRegistered = true;
			setSurfaceTextureListener(this);
		}
	}

	/**
	 * Safety net: if windowName prop is never set, setWindowName() is never called
	 * and the listener is never registered. onAttachedToWindow() fires after all
	 * props have been applied (both Fabric and Paper), making it a safe fallback.
	 */
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (!isDestroyed && !listenerRegistered) {
			listenerRegistered = true;
			setSurfaceTextureListener(this);
		}
	}

	public String getWindowName() {
		return windowName;
	}

	// ── TextureView.SurfaceTextureListener ────────────────────────────────────

	@Override
	public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int width, int height) {
		if (isDestroyed) return;
		Log.i(TAG, String.format("onSurfaceTextureAvailable: '%s' %dx%d", windowName, width, height));
		// Release any stale surface first (e.g. back-to-back availability callbacks,
		// or setSurfaceTextureListener called a second time when surface is already live).
		if (mCurrentSurface != null) {
			RTNLibGodot.getInstance().removeWindow(windowName);
			mCurrentSurface.release();
			mCurrentSurface = null;
		}
		mCurrentSurface = new Surface(st);
		RTNLibGodot.getInstance().updateWindow(windowName, mCurrentSurface, width, height);
	}

	@Override
	public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int width, int height) {
		if (isDestroyed) return;
		Log.i(TAG, String.format("onSurfaceTextureSizeChanged: '%s' %dx%d", windowName, width, height));
		if (mCurrentSurface != null && mCurrentSurface.isValid()) {
			RTNLibGodot.getInstance().updateWindow(windowName, mCurrentSurface, width, height);
		}
	}

	@Override
	public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) {
		Log.i(TAG, String.format("onSurfaceTextureDestroyed: '%s'", windowName));
		// destroy() may have already cleaned up (onDropViewInstance path).
		// releaseCurrentSurface() is idempotent (nulls mCurrentSurface after first call),
		// so it is safe to call here regardless.
		releaseCurrentSurface();
		// Return true so the platform releases the SurfaceTexture immediately.
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {
		// no-op — Godot drives its own render loop
	}

	// ── Surface lifecycle helpers ─────────────────────────────────────────────

	/**
	 * Releases the Surface held by this view and notifies Godot.
	 * Safe to call multiple times — guarded by mCurrentSurface null-check.
	 * Does NOT depend on isDestroyed so that both destroy() and
	 * onSurfaceTextureDestroyed() can safely call it.
	 */
	private void releaseCurrentSurface() {
		// Tell Godot first, while the surface reference is still alive.
		RTNLibGodot.getInstance().removeWindow(windowName);
		// Then release the Java-side Surface handle.
		if (mCurrentSurface != null) {
			mCurrentSurface.release();
			mCurrentSurface = null;
		}
	}

	// ── Called by RTNGodotViewManager.onDropViewInstance ─────────────────────

	public void destroy() {
		if (isDestroyed) return;
		// Stop receiving any future SurfaceTexture callbacks first.
		setSurfaceTextureListener(null);
		listenerRegistered = false;
		// Notify Godot and release the surface before marking destroyed,
		// so releaseCurrentSurface() can still reach RTNLibGodot.
		releaseCurrentSurface();
		isDestroyed = true;
		setVisibility(android.view.View.GONE);
	}

	// ── Touch / pointer input ─────────────────────────────────────────────────

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

	@Override
	public void requestPointerCapture() {
		super.requestPointerCapture();
		GodotInputHandler handler = getInputHandler();
		if (handler != null) handler.onPointerCaptureChange(true);
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