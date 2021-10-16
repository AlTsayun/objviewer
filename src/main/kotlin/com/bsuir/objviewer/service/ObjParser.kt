package com.bsuir.objviewer.service

import com.bsuir.objviewer.model.ObjEntries
import com.bsuir.objviewer.model.ObjEntry.*

class ObjParser {

    private val whitespaces = "\\s+".toPattern()

    fun parse(lines: List<String>): ObjEntries {

        val entries = ObjEntries()
        lines
            .map { it.trim() }
            .forEach {
                when {
                    it.startsWith("v ") -> parseVertex(it).let(entries.vertexes::add)
                    it.startsWith("vt ") -> parseTexture(it).let(entries.textures::add)
                    it.startsWith("vn ") -> parseNormal(it).let(entries.normals::add)
                    it.startsWith("f ") -> parseFace(it, entries).let(entries.faces::add)
                }
            }
        return entries
    }

    private fun parseVertex(line: String): Vertex {
        val parts = line.split(whitespaces)
        val (_, x, y, z) = parts
        val w = parts.getOrNull(4)
        return Vertex(
            x = x.toDouble(),
            y = y.toDouble(),
            z = z.toDouble(),
            w = w?.toDouble() ?: 1.0,
        )
    }

    private fun parseTexture(line: String): Texture {
        val parts = line.split(whitespaces)
        val u = parts[1]
        val v = parts.getOrNull(2)
        val w = parts.getOrNull(3)
        return Texture(
            u = u.toDouble(),
            v = v?.toDouble(),
            w = w?.toDouble(),
        )
    }

    private fun parseNormal(line: String): Normal {
        val (_, i, j, k) = line.split(whitespaces)
        return Normal(
            i = i.toDouble(),
            j = j.toDouble(),
            k = k.toDouble(),
        )
    }

    private fun parseFace(line: String, entries: ObjEntries): Face {
        fun String.toIndex(size: Int) = this.toInt().let {
            when {
                it > 0 -> it - 1
                it < 0 -> size + it
                else -> throw RuntimeException()
            }
        }

        return line.split(whitespaces)
            .asSequence()
            .drop(1)
            .map { group ->
                val parts = group.split("/")
                Face.Item(
                    vertex = parts[0].toIndex(size = entries.vertexes.size)
                        .let { entries.vertexes[it] },
                    texture = parts.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?.toIndex(size = entries.textures.size)
                        ?.let {entries.textures[it]} ,
                    normal = parts.getOrNull(2)
                        ?.takeIf { it.isNotBlank() }
                        ?.toIndex(size = entries.normals.size)
                        ?.let { entries.normals[it]},
                )
            }
            .toList()
            .let { items -> Face(items) }
    }
}
