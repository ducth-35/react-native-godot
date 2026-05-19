/**************************************************************************/
/*  RTNLibGodot.java                                                      */
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

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotHost;
import org.godotengine.godot.GodotIO;
import org.godotengine.godot.GodotLib;
import org.godotengine.godot.GodotLibImpl;
import org.godotengine.godot.GodotRenderView;
import org.godotengine.godot.IGodotLib;
import org.godotengine.godot.input.GodotInputHandler;
import org.godotengine.godot.io.directory.DirectoryAccessHandler;
import org.godotengine.godot.io.file.FileAccessHandler;
import org.godotengine.godot.plugin.AndroidRuntimePlugin;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.GodotPluginRegistry;
import org.godotengine.godot.tts.GodotTTS;
import org.godotengine.godot.utils.GodotNetUtils;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RTNLibGodot implements IGodotLib, GodotHost, GodotRenderView {
	private static final String TAG = "LibGodot";

	private static boolean inited = false;

	private static Activity mActivity;

	private static RTNLibGodot instance = null;

	private Godot godot;

	private FrameLayout godotContainerLayout;

	private GodotInputHandler mInputHandler;

	/**
	 * An off-screen ImageReader used only to provide a valid Surface for Godot engine
	 * initialization. Godot needs an ANativeWindow to start; the ImageReader Surface
	 * satisfies that requirement without requiring SurfaceControl (API 29+).
	 *
	 * API requirement: ImageReader is available from API 19. All frames produced here
	 * are acquired-and-closed immediately to prevent the queue from blocking Godot.
	 */
	private static ImageReader initImageReader;
	private static Surface    initSurface;
	/** Background thread that drains ImageReader frames so Godot is never blocked. */
	private static android.os.HandlerThread imageReaderThread;
	private static android.os.Handler      imageReaderHandler;

	/** Width/height passed to the engine at initialization time. */
	private static int surfaceWidth;
	private static int surfaceHeight;

	private RTNLibGodot() {}

	public static RTNLibGodot getInstance() {
		if (RTNLibGodot.instance == null) {
			RTNLibGodot.instance = new RTNLibGodot();
		}
		return RTNLibGodot.instance;
	}

	// ── IGodotLib / GodotRenderView boilerplate ───────────────────────────────

	@Override
	public boolean initialize(Godot godot, AssetManager assetManager, GodotIO godotIO, GodotNetUtils godotNetUtils, DirectoryAccessHandler directoryAccessHandler, FileAccessHandler fileAccessHandler, boolean b) {
		ClassLoader loader = RTNLibGodot.class.getClassLoader();

		if (initSurface == null || !initSurface.isValid()) {
			Log.e(TAG, "Init surface is not valid. Ensure init() was called before initialize().");
			return false;
		}

		initialize(
				assetManager,
				godotNetUtils,
				directoryAccessHandler,
				fileAccessHandler,
				godotIO,
				initSurface,
				surfaceWidth,
				surfaceHeight,
				godot,
				mActivity,
				loader);
		return true;
	}

	@Override public void ondestroy() {}
	@Override public boolean setup(String[] strings, GodotTTS godotTTS) { return true; }
	@Override public void resize(Surface surface, int i, int i1) {}
	@Override public void newcontext(Surface surface) {}
	@Override public void back() {}
	@Override public boolean step() { return false; }
	@Override public void ttsCallback(int i, int i1, int i2) {}
	@Override public void dispatchTouchEvent(int i, int i1, int i2, float[] floats, boolean b) {
		dispatchTouchEvent("", i, i1, i2, floats, b);
	}
	@Override public void dispatchMouseEvent(int i, int i1, float v, float v1, float v2, float v3, boolean b, boolean b1, float v4, float v5, float v6) {}
	@Override public void magnify(float v, float v1, float v2) {}
	@Override public void pan(float v, float v1, float v2, float v3) {}
	@Override public void accelerometer(float v, float v1, float v2) {}
	@Override public void gravity(float v, float v1, float v2) {}
	@Override public void magnetometer(float v, float v1, float v2) {}
	@Override public void gyroscope(float v, float v1, float v2) {}
	@Override public void key(int i, int i1, int i2, boolean b, boolean b1) {}
	@Override public void joybutton(int i, int i1, boolean b) {}
	@Override public void joyaxis(int i, int i1, float v) {}
	@Override public void joyhat(int i, int i1, int i2) {}
	@Override public void joyconnectionchanged(int i, boolean b, String s) {}
	@Override public void focusin() {}
	@Override public void focusout() {}
	@Override public native String getGlobal(String s);
	@Override public native String[] getRendererInfo();
	@Override public String getEditorSetting(String s) { return ""; }
	@Override public void setEditorSetting(String s, Object o) {}
	@Override public Object getEditorProjectMetadata(String s, String s1, Object o) { return null; }
	@Override public void setEditorProjectMetadata(String s, String s1, Object o) {}
	@Override public void requestPermissionResult(String s, boolean b) {}
	@Override public void onNightModeChanged() {}
	@Override public void hardwareKeyboardConnected(boolean b) {}
	@Override public void filePickerCallback(boolean b, String[] strings) {}
	@Override public void setVirtualKeyboardHeight(int i) {}
	@Override public void onRendererResumed() {}
	@Override public void onRendererPaused() {}
	@Override public boolean shouldDispatchInputToRenderThread() { return false; }
	@Override public String getProjectResourceDir() { return ""; }
	@Override public boolean isEditorHint() { return false; }
	@Override public boolean isProjectManagerHint() { return false; }
	@Override public boolean providesRenderView() { return true; }
	@Override public GodotRenderView getRenderView() { return this; }
	@Nullable @Override public Activity getActivity() { return mActivity; }
	public Godot getGodot() { return godot; }
	@Override public SurfaceView getView() { return null; }
	@Override public void startRenderer() {}
	@Override public void queueOnRenderThread(Runnable runnable) {}
	@Override public void onActivityPaused() {}
	@Override public void onActivityStopped() {}
	@Override public void onActivityResumed() {}
	@Override public void onActivityStarted() {}
	@Override public void onActivityDestroyed() {}
	@Override public GodotInputHandler getInputHandler() { return mInputHandler; }
	@Override public void configurePointerIcon(int i, String s, float v, float v1) {}
	@Override public void setPointerIcon(int i) {}

	// ── Window management (Surface-direct, no SurfaceControl) ─────────────────

	/**
	 * Called by RTNGodotView when its TextureView has a valid Surface.
	 * Passes the Surface directly to the native layer. No SurfaceControl needed.
	 */
	public void updateWindow(String name, Surface surface, int width, int height) {
		if (surface == null || !surface.isValid()) {
			Log.w(TAG, "updateWindow called with invalid surface for window: '" + name + "'");
			return;
		}
		Log.i(TAG, String.format("updateWindow: '%s' %dx%d", name, width, height));
		updateWindowNative(name, surface, width, height);
	}

	/**
	 * Called by RTNGodotView when its TextureView surface is destroyed.
	 * The main window ("") is left alone — Godot manages its own lifecycle there.
	 */
	public void removeWindow(String name) {
		Log.i(TAG, "removeWindow: '" + name + "'");
		removeWindowNative(name);
	}

	// ── Initialization ─────────────────────────────────────────────────────────

	public void init(Activity activity) {
		if (inited) {
			Log.w(TAG, "Already initialized, skipping init()");
			return;
		}

		mActivity = activity;

		if (mActivity == null) {
			Log.e(TAG, "Activity not set, abort init");
			return;
		}

		Log.d(TAG, "Initializing RTNLibGodot...");
		DisplayMetrics metrics = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

		surfaceWidth  = metrics.widthPixels;
		surfaceHeight = metrics.heightPixels;

		// Create an offscreen ImageReader Surface to satisfy Godot's initialization
		// requirement of a valid ANativeWindow. No SurfaceControl (API 29) needed.
		// ImageReader is available from API 19.
		initImageReader = ImageReader.newInstance(
				surfaceWidth, surfaceHeight,
				PixelFormat.RGBA_8888,
				/* maxImages= */ 2);

		// Drain frames on a dedicated background thread so the main thread is never
		// blocked and no ANR risk is introduced during rapid navigation cycles.
		imageReaderThread = new android.os.HandlerThread("GodotInitImageReader");
		imageReaderThread.start();
		imageReaderHandler = new android.os.Handler(imageReaderThread.getLooper());
		initImageReader.setOnImageAvailableListener(reader -> {
			android.media.Image img = reader.acquireLatestImage();
			if (img != null) img.close();
		}, imageReaderHandler);

		initSurface = initImageReader.getSurface();

		GodotLib.setGodotLibImpl(RTNLibGodot.getInstance());
		godot = Godot.getInstance(mActivity);
		godot.setActivity(mActivity);

		Set<GodotPlugin> runtimePlugins = new HashSet<>();
		runtimePlugins.add(new AndroidRuntimePlugin(godot));
		runtimePlugins.addAll(getHostPlugins());

		List<String> commands = new ArrayList<>();

		if (!godot.initEngine(this, commands, runtimePlugins)) {
			Log.e(TAG, "Unable to initialize Godot engine layer");
			return;
		}

		mInputHandler = new GodotInputHandler(mActivity, godot);

		inited = true;
		Log.d(TAG, "RTNLibGodot initialized successfully (ImageReader init surface: "
				+ surfaceWidth + "x" + surfaceHeight + ")");
	}

	public void shutdown() {
		if (inited) {
			Log.d(TAG, "Shutting down RTNLibGodot...");
			cleanup();
			releaseInitSurface();
			inited = false;
		}
	}

	private static void releaseInitSurface() {
		if (initSurface != null) {
			initSurface.release();
			initSurface = null;
		}
		if (initImageReader != null) {
			initImageReader.close();
			initImageReader = null;
		}
		if (imageReaderThread != null) {
			imageReaderThread.quitSafely();
			imageReaderThread = null;
			imageReaderHandler = null;
		}
	}

	// ── Native methods ─────────────────────────────────────────────────────────

	private static native void initialize(AssetManager asset_manager,
			GodotNetUtils net_utils,
			DirectoryAccessHandler directoryAccessHandler,
			FileAccessHandler fileAccessHandler,
			GodotIO io, Surface mainSurface, int width, int height,
			Godot godot, Activity host_activity, ClassLoader appClassLoader);

	private native void cleanup();

	public native void dispatchTouchEvent(String windowName, int event, int pointer, int pointerCount, float[] positions, boolean doubleTap);

	public native void dispatchMouseEvent(String windowName, int event, int buttonMask, float x, float y, float deltaX, float deltaY, boolean doubleClick, boolean sourceMouseRelative, float pressure, float tiltX, float tiltY);

	private native void updateWindowNative(String windowName, Surface surface, int width, int height);

	private native void removeWindowNative(String windowName);

	private static native void registerWindowUpdateCallbackNative(String name, Object handle, Runnable r);

	private static native void unregisterWindowUpdateCallbackNative(Object handle);

	// ── Plugin management ──────────────────────────────────────────────────────

	public Set<GodotPlugin> hostPlugins = new HashSet<>();

	public void addHostPlugin(GodotPlugin plugin) {
		hostPlugins.add(plugin);
	}

	public Set<GodotPlugin> getHostPlugins() {
		return hostPlugins;
	}
}
