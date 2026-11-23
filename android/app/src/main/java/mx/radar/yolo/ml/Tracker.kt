package mx.radar.yolo.ml

/** Tracker IoU simple: asociación greedy por IoU >= umbral, IDs incrementales. */
class Tracker(private val umbralIou: Float = 0.3f, private val memoriaS: Double = 1.0) {
    private class Pista(val id: Int, var det: Deteccion, var visto: Double)

    private val pistas = ArrayList<Pista>()
    private var siguiente = 1

    /** Devuelve cada detección con su ID de pista. [t] en segundos. */
    fun actualizar(dets: List<Deteccion>, t: Double): List<Pair<Int, Deteccion>> {
        pistas.removeAll { t - it.visto > memoriaS }

        data class Par(val p: Int, val d: Int, val iou: Float)
        val pares = ArrayList<Par>()
        for (pi in pistas.indices) for (di in dets.indices) {
            val v = iou(pistas[pi].det, dets[di])
            if (v >= umbralIou) pares.add(Par(pi, di, v))
        }
        pares.sortByDescending { it.iou }

        val pistaUsada = BooleanArray(pistas.size)
        val asignado = arrayOfNulls<Int>(dets.size)
        for (par in pares) {
            if (pistaUsada[par.p] || asignado[par.d] != null) continue
            pistaUsada[par.p] = true
            asignado[par.d] = pistas[par.p].id
            pistas[par.p].det = dets[par.d]
            pistas[par.p].visto = t
        }

        val salida = ArrayList<Pair<Int, Deteccion>>()
        for (di in dets.indices) {
            val id = asignado[di] ?: Pista(siguiente++, dets[di], t).also { pistas.add(it) }.id
            salida.add(id to dets[di])
        }
        return salida
    }
}
