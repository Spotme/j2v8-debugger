package com.alexii.j2v8debugger

import android.content.Context
import android.support.annotation.VisibleForTesting
import android.util.Log
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.debug.DebugHandler
import com.facebook.stetho.InspectorModulesProvider
import com.facebook.stetho.Stetho
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

object StethoHelper {
    var debugger: Debugger? = null
        private set
        @VisibleForTesting get

    private var v8DebuggerRef: WeakReference<DebugHandler>? = null
    private var v8ExecutorRef: WeakReference<ExecutorService>? = null

    /**
     * @return Similar to [Stetho.defaultInspectorModulesProvider] but contains [Debugger] for [V8]
     */
    @JvmStatic
    fun defaultInspectorModulesProvider(context: Context, scriptSourceProvider: ScriptSourceProvider): InspectorModulesProvider {
        return InspectorModulesProvider {
            try {
                getDefaultInspectorModulesWithDebugger(context, scriptSourceProvider)
            } catch (e: Throwable) { //v8 throws Error instead of Exception on wrong thread access, etc.
                Log.e(Debugger.TAG, "Unable to init Stetho with V8 Debugger. Default set-up will be used", e)

                getDefaultInspectorModules(context)
            }
        }
    }

    @VisibleForTesting
    fun getDefaultInspectorModulesWithDebugger(context: Context, scriptSourceProvider: ScriptSourceProvider): Iterable<ChromeDevtoolsDomain> {
        val defaultInspectorModules = getDefaultInspectorModules(context)

        //remove work-around when https://github.com/facebook/stetho/pull/600 is merged
        val inspectorModules = ArrayList<ChromeDevtoolsDomain>()
        for (defaultModule in defaultInspectorModules) {
            if (FacebookDebuggerStub::class.java != defaultModule.javaClass) inspectorModules.add(defaultModule)
        }

        debugger = Debugger(scriptSourceProvider)
        inspectorModules.add(debugger!!)

        bindV8ToChromeDebuggerIfReady()

        return inspectorModules
    }

    /**
     * @param v8Executor executor, where V8 should be previously initialized and further will be called on.
     */
    fun initializeWithV8Debugger(v8Debugger: DebugHandler, v8Executor: ExecutorService) {
        v8DebuggerRef = WeakReference(v8Debugger)
        v8ExecutorRef = WeakReference(v8Executor)

        bindV8ToChromeDebuggerIfReady()
    }

    private fun bindV8ToChromeDebuggerIfReady() {
        val chromeDebuggerAttached = debugger != null

        val v8Debugger = v8DebuggerRef?.get()
        val v8Executor = v8ExecutorRef?.get()
        val v8DebuggerInitialized = v8Debugger != null && v8Executor != null

        if (v8DebuggerInitialized && chromeDebuggerAttached) {
            v8Executor!!.execute { bindV8DebuggerToChromeDebugger(debugger!!, v8Debugger!!, v8Executor!!) }
        }
    }

    /**
     * Shoulds be called when both Chrome debugger and V8 debugger is ready
     *  (When Chrome DevTools UI is open and V8 is created in debug mode with debugger object).
     */
    private fun bindV8DebuggerToChromeDebugger(chromeDebugger: Debugger, v8Debugger: DebugHandler, v8Executor: ExecutorService) {
        chromeDebugger.initialize(v8Debugger, v8Executor)
    }

    private fun getDefaultInspectorModules(context: Context): Iterable<ChromeDevtoolsDomain> {
        return Stetho.DefaultInspectorModulesBuilder(context)
                .finish()
    }
}