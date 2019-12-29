package com.teamwizardry.librarianlib.models

import com.teamwizardry.librarianlib.math.CoordinateSpace3D
import com.teamwizardry.librarianlib.math.Matrix3d
import com.teamwizardry.librarianlib.math.Matrix4d
import com.teamwizardry.librarianlib.math.MutableMatrix3d
import com.teamwizardry.librarianlib.math.MutableMatrix4d
import com.teamwizardry.librarianlib.math.Quaternion
import com.teamwizardry.librarianlib.math.WorldSpace
import com.teamwizardry.librarianlib.math.cross
import com.teamwizardry.librarianlib.math.minus
import com.teamwizardry.librarianlib.math.plus
import com.teamwizardry.librarianlib.math.times
import com.teamwizardry.librarianlib.math.vec
import de.javagl.obj.ActAction
import de.javagl.obj.ActObject
import de.javagl.obj.FloatTuple
import de.javagl.obj.FloatTuples
import de.javagl.obj.ObjArmature
import de.javagl.obj.ObjBoneIndex
import de.javagl.obj.ObjBoneIndexes
import de.javagl.obj.ObjUtils
import de.javagl.obj.Objs
import de.javagl.obj.ReadableObj
import net.minecraft.util.math.Vec3d

// much of this logic is based around this: https://veeenu.github.io/2014/05/09/implementing-skeletal-animation.html

class ModelInstance(val root: Model) {
    private var _model = Objs.create()
    val model: ReadableObj get() = _model
    var armatures: List<Armature> = listOf()
        private set
    private val bones: MutableMap<ObjBoneIndex, Bone> = mutableMapOf()
    private var armatureMap: Map<String, Armature> = mapOf()

    init {
        root.register(this)
        load()
    }

    operator fun get(name: String): Armature = armatureMap.getValue(name)

    fun updateTransforms() {
        fun update(bone: Bone) {
            bone.updateMatrices()
            bone.children.forEach {
                update(it)
            }
        }
        armatures.forEach { armature ->
            armature.rootBones.forEach {
                update(it)
            }
        }

        for(vertexIndex in 0 until model.numVertices) {
            val vertex = root.obj.getVertex(vertexIndex)
            val weights = root.obj.getWeights(vertexIndex)
            var weightSum = 0.0
            var xSum = 0.0
            var ySum = 0.0
            var zSum = 0.0
            if(weights != null) {
                for(weightIndex in 0 until weights.numWeights) {
                    val boneIndex = weights.getBoneIndex(weightIndex)
                    val weight = weights.getWeight(weightIndex)
                    val bone = bones[boneIndex] ?: continue
                    val transformed = bone.finalMatrix * vec(vertex.x, vertex.y, vertex.z)
                    xSum += transformed.x * weight
                    ySum += transformed.y * weight
                    zSum += transformed.z * weight
                    weightSum += weight
                }
            }
            if(weightSum != 0.0) {
                _model.setVertex(vertexIndex, FloatTuples.create(
                    (xSum / weightSum).toFloat(),
                    (ySum / weightSum).toFloat(),
                    (zSum / weightSum).toFloat()
                ))
            }
        }

        for(faceIndex in 0 until model.numFaces) {
            val face = model.getFace(faceIndex)
            if(!face.containsNormalIndices()) continue
            if(face.numVertices != 3) continue

            val a = model.getVertex(face.getVertexIndex(0)).toVec3d()
            val b = model.getVertex(face.getVertexIndex(1)).toVec3d()
            val c = model.getVertex(face.getVertexIndex(2)).toVec3d()
            val normal = ((c - b) cross (a - b)).normalize()
            val normalTuple = normal.toFloatTuple()
            _model.setNormal(face.getNormalIndex(0), normalTuple)
            _model.setNormal(face.getNormalIndex(1), normalTuple)
            _model.setNormal(face.getNormalIndex(2), normalTuple)
        }
    }

    fun load() {
        // we assume that root.obj has been passed through ObjUtils.convertToRenderable
        _model = Objs.create()
        ObjUtils.add(root.obj, _model)
        val armatures = mutableListOf<Armature>()
        for(armatureIndex in 0 until _model.numArmatures) {
            val objArmature = _model.getArmature(armatureIndex)
            val armature = Armature(this, objArmature.name, armatureIndex)
            armatures.add(armature)
            armature.loadObj(model, objArmature)
            armature.bones.forEachIndexed { boneIndex, bone ->
                this.bones[ObjBoneIndexes.create(armatureIndex, boneIndex)] = bone
            }
        }
        this.armatures = armatures
        this.armatureMap = armatures.associateBy { it.name }
    }
}

class Armature(val model: ModelInstance, val name: String, val index: Int) {
    var bones: List<Bone> = listOf()
        private set
    var rootBones: List<Bone> = listOf()
        private set
    private var boneMap: Map<String, Bone> = mapOf()
    var actObject: ActObject? = null
        private set

    operator fun get(name: String): Bone = boneMap.getValue(name)

    private var action: ActAction? = null

    fun startAction(name: String) {
        action = actObject?.getAction(name)
    }

    fun seekFrame(frame: Int) {
        val action = action ?: return
        for(i in 0 until action.channelCount) {
            val channel = action.getChannel(i)
            val boneName = channel.name.substringBeforeLast('.')
            val property = channel.name.substringAfterLast('.')
            val value = channel.getValue(frame.toFloat()).toDouble()
            val bone = boneMap[boneName] ?: continue
            when(property) {
                "rx" -> bone.rotation = Quaternion(value, bone.rotation.y, bone.rotation.z, bone.rotation.w)
                "ry" -> bone.rotation = Quaternion(bone.rotation.x, value, bone.rotation.z, bone.rotation.w)
                "rz" -> bone.rotation = Quaternion(bone.rotation.x, bone.rotation.y, value, bone.rotation.w)
                "rw" -> bone.rotation = Quaternion(bone.rotation.x, bone.rotation.y, bone.rotation.z, value)
                "tx" -> bone.translation = vec(value, bone.translation.y, bone.translation.z)
                "ty" -> bone.translation = vec(bone.translation.x, value, bone.translation.z)
                "tz" -> bone.translation = vec(bone.translation.x, bone.translation.y, value)
            }
        }
    }

    fun loadObj(obj: ReadableObj, objArmature: ObjArmature) {
        val bones = mutableListOf<Bone>()
        val rootBones = mutableListOf<Bone>()

        for(boneIndex in 0 until objArmature.numBones) {
            val objBone = objArmature.getBone(boneIndex)
            val parent = if(objBone.parent < 0) null else bones[objBone.parent]
            val position = vec(objBone.position.x, objBone.position.y, objBone.position.z)
            val rotation = Quaternion(objBone.rotation.x, objBone.rotation.y, objBone.rotation.z, objBone.rotation.w)
            val matrix = Matrix4d.IDENTITY.rotate(rotation).translate(position)
            val bone = Bone(parent, objBone.name, objBone.length.toDouble(), matrix)
            bones.add(bone)
            if(parent == null)
                rootBones.add(bone)
        }

        this.bones = bones
        this.rootBones = rootBones
        this.boneMap = bones.associateBy { it.name }
        this.actObject = model.root.actions.getObject(this.name)
    }
}

class Bone(
    /**
     * This bone's parent, or null if it a root bone
     */
    val parent: Bone?,
    /**
     * This bone's name
     */
    val name: String,
    /**
     * The length of this bone
     */
    val length: Double,
    /**
     * The transformation to take points in this bone's local space into the world space
     */
    val restToWorld: Matrix4d
): CoordinateSpace3D {
    val children: MutableList<Bone> = mutableListOf()
    init {
        parent?.children?.add(this)
    }

    /**
     * The transformation to take points in the world space into this bone's local space
     */
    val worldToRest: Matrix4d = restToWorld.invert()

    /**
     * The local transformation of this bone at rest, relative to its parent.
     */
    val restTransform: Matrix4d = (parent?.worldToRest ?: Matrix4d.IDENTITY) * restToWorld

    /**
     *
     */
    var localTransform: Matrix4d = Matrix4d.IDENTITY

    /**
     *
     */
    var rotation: Quaternion = Quaternion()

    /**
     *
     */
    var translation: Vec3d = vec(0, 0, 0)

    override val parentSpace: CoordinateSpace3D? = parent ?: WorldSpace
    override var matrix: Matrix4d = Matrix4d.IDENTITY
        private set
    override var inverseMatrix: Matrix4d = Matrix4d.IDENTITY
        private set

    var finalMatrix: Matrix4d = Matrix4d.IDENTITY
        private set

    fun updateMatrices() {
        val local = MutableMatrix4d()
        local.rotate(rotation)
        local.translate(translation)
        localTransform = local.toImmutable()

        matrix = restTransform * localTransform
        inverseMatrix = matrix.invert()
        finalMatrix = this.conversionMatrixTo(WorldSpace) * worldToRest
    }
}

private fun FloatTuple.toVec3d(): Vec3d {
    return vec(this.x, this.y, this.z)
}

private fun Vec3d.toFloatTuple(): FloatTuple {
    return FloatTuples.create(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}