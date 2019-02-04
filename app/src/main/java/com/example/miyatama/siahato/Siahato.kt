package com.example.miyatama.siahato

import android.content.Context
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable

class Siahato(
    context: Context,
    scale:Float,
    renderable: ModelRenderable ): Node() {
    private var scale: Float = 0.0F
    private var renderable: ModelRenderable
    private var context: Context

    private val INFO_CARD_Y_POS_COEFF = 0.55f

    init {
        this.context = context
        this.scale = scale
        this.renderable = renderable
        super.setLocalScale(Vector3(this.scale, this.scale, this.scale))
        super.setRenderable(this.renderable)

    }

    override fun onActivate() {
        super.onActivate()
        if (scene == null) {
            throw IllegalStateException("Scene is null!")
        }

    }

}