package com.teamwizardry.librarianlib.models.testmod.models

import com.teamwizardry.librarianlib.models.ModelInstance
import com.teamwizardry.librarianlib.models.ModelRenderer
import com.teamwizardry.librarianlib.models.testmod.TestModel
import com.teamwizardry.librarianlib.testbase.objects.TestEntity

object StaticModel: TestModel<Any?>("static", "Static model") {
    val modelInstance = ModelInstance(model)

    override fun createState(): Any? = null

    override fun tickState(state: Any?) {

    }

    override fun render(entity: TestEntity, partialTicks: Float, state: Any?) {
        ModelRenderer.render(modelInstance)
    }
}