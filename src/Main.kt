import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import kotlin.math.sqrt

// --- ESTRUCTURAS DE DATOS ---
data class Nodo(val id: Int, val x: Double, val y: Double)
data class Arista(val desde: Int, val hasta: Int, var peso: Int)
data class RutaSegmento(val de: Int, val a: Int)

// --- ESTADO GLOBAL DEL GRAFO ---
object GrafoState {
    val nodos = mutableListOf<Nodo>()
    val aristas = mutableListOf<Arista>()
    var modo = "nodo"
    var nodoSeleccionado: Int? = null
    val rutaResaltada = mutableListOf<RutaSegmento>()
    val logs = mutableListOf<String>().apply { add("Haz clic en el lienzo para añadir nodos...") }

    // Variables de control para comunicarse con el Frontend
    var solicitarArista: String? = null
    var solicitarEdicionArista: String? = null
    var alert: String? = null

    fun detectarNodo(x: Double, y: Double): Int? {
        for (nodo in nodos) {
            val dist = sqrt((x - nodo.x) * (x - nodo.x) + (y - nodo.y) * (y - nodo.y))
            if (dist < 20) return nodo.id
        }
        return null
    }

    // Encuentra si se hizo clic cerca del punto medio de una arista para poder editarla
    fun detectarArista(x: Double, y: Double): Arista? {
        for (arista in aristas) {
            val n1 = nodos.find { it.id == arista.desde }
            val n2 = nodos.find { it.id == arista.hasta }
            if (n1 != null && n2 != null) {
                val midX = (n1.x + n2.x) / 2
                val midY = (n1.y + n2.y) / 2
                val dist = sqrt((x - midX) * (x - midX) + (y - midY) * (y - midY))
                if (dist < 15) return arista // Margen de tolerancia de 15 píxeles alrededor del peso
            }
        }
        return null
    }

    fun toJson(): String {
        val nodosJson = nodos.joinToString(",") { """{"id":${it.id},"x":${it.x},"y":${it.y}}""" }
        val aristasJson = aristas.joinToString(",") { """{"desde":${it.desde},"hasta":${it.hasta},"peso":${it.peso}}""" }
        val rutaJson = rutaResaltada.joinToString(",") { """{"de":${it.de},"a":${it.a}}""" }
        val logsJson = logs.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }

        val json = """{
            "nodos": [$nodosJson],
            "aristas": [$aristasJson],
            "modo": "$modo",
            "nodoSeleccionado": ${nodoSeleccionado ?: "null"},
            "rutaResaltada": [$rutaJson],
            "logs": [$logsJson],
            "solicitarArista": ${solicitarArista ?: "null"},
            "solicitarEdicionArista": ${solicitarEdicionArista ?: "null"},
            "alert": ${if (alert != null) "\"$alert\"" else "null"}
        }"""
        solicitarArista = null
        solicitarEdicionArista = null
        alert = null
        return json
    }
}

fun main() {
    val servidor = HttpServer.create(InetSocketAddress(8080), 0)

    servidor.createContext("/") { intercambio ->
        val archivoHtml = File("src/resources/index.html")
        if (archivoHtml.exists()) {
            val respuesta = archivoHtml.readBytes()
            intercambio.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            intercambio.sendResponseHeaders(200, respuesta.size.toLong())
            val os: OutputStream = intercambio.responseBody
            os.write(respuesta)
            os.close()
        } else {
            val error404 = "Error 404: No se encontró index.html en src/resources/".toByteArray()
            intercambio.sendResponseHeaders(404, error404.size.toLong())
            val os: OutputStream = intercambio.responseBody
            os.write(error404)
            os.close()
        }
    }

    servidor.createContext("/api") { intercambio ->
        val query = intercambio.requestURI.query
        val params = parseQueryParams(query)
        val action = params["action"]

        synchronized(GrafoState) {
            when (action) {
                "get" -> { /* Retorna estado */ }

                "cambiarModo" -> {
                    GrafoState.modo = params["modo"] ?: "nodo"
                    GrafoState.nodoSeleccionado = null
                    GrafoState.rutaResaltada.clear()
                    val msg = when(GrafoState.modo) {
                        "nodo" -> "Crear Nodos"
                        "arista" -> "Conectar Aristas"
                        else -> "Editar Pesos (Haz clic en el número de una arista)"
                    }
                    GrafoState.logs.add("Cambiado a modo: $msg")
                }

                "click" -> {
                    val x = params["x"]?.toDoubleOrNull() ?: 0.0
                    val y = params["y"]?.toDoubleOrNull() ?: 0.0

                    if (GrafoState.modo == "nodo") {
                        val nuevoId = GrafoState.nodos.size
                        GrafoState.nodos.add(Nodo(nuevoId, x, y))
                        GrafoState.logs.add("Nodo $nuevoId creado en (${x.toInt()}, ${y.toInt()})")
                        GrafoState.rutaResaltada.clear()
                    } else if (GrafoState.modo == "arista") {
                        val nodoClickeado = GrafoState.detectarNodo(x, y)
                        if (nodoClickeado != null) {
                            if (GrafoState.nodoSeleccionado == null) {
                                GrafoState.nodoSeleccionado = nodoClickeado
                                GrafoState.logs.add("Seleccionado Nodo origen: $nodoClickeado")
                            } else {
                                if (GrafoState.nodoSeleccionado != nodoClickeado) {
                                    GrafoState.solicitarArista = """{"desde":${GrafoState.nodoSeleccionado},"hasta":$nodoClickeado}"""
                                }
                                GrafoState.nodoSeleccionado = null
                            }
                        }
                    } else if (GrafoState.modo == "editar") {
                        val aristaClickeada = GrafoState.detectarArista(x, y)
                        if (aristaClickeada != null) {
                            GrafoState.solicitarEdicionArista = """{"desde":${aristaClickeada.desde},"hasta":${aristaClickeada.hasta},"actual":${aristaClickeada.peso}}"""
                        }
                    }
                }

                "addArista" -> {
                    val desde = params["desde"]?.toIntOrNull() ?: -1
                    val hasta = params["hasta"]?.toIntOrNull() ?: -1
                    val peso = params["peso"]?.toIntOrNull() ?: -1
                    if (desde != -1 && hasta != -1 && peso >= 0) {
                        GrafoState.aristas.add(Arista(desde, hasta, peso))
                        GrafoState.logs.add("Arista conectada: $desde ↔ $hasta (Peso: $peso)")
                        GrafoState.rutaResaltada.clear()
                    }
                }

                "actualizarPeso" -> {
                    val desde = params["desde"]?.toIntOrNull() ?: -1
                    val hasta = params["hasta"]?.toIntOrNull() ?: -1
                    val nuevoPeso = params["peso"]?.toIntOrNull() ?: -1
                    if (desde != -1 && hasta != -1 && nuevoPeso >= 0) {
                        val arista = GrafoState.aristas.find {
                            (it.desde == desde && it.hasta == hasta) || (it.desde == hasta && it.hasta == desde)
                        }
                        if (arista != null) {
                            arista.peso = nuevoPeso
                            GrafoState.logs.add("Peso actualizado en arista $desde ↔ $hasta: Nuevo peso = $nuevoPeso")
                            GrafoState.rutaResaltada.clear()
                        }
                    }
                }

                "eliminar" -> {
                    if (GrafoState.nodos.isNotEmpty()) {
                        val borrado = GrafoState.nodos.removeAt(GrafoState.nodos.size - 1)
                        GrafoState.aristas.removeIf { it.desde == borrado.id || it.hasta == borrado.id }
                        GrafoState.logs.add("Nodo ${borrado.id} eliminado junto con sus conexiones.")
                        GrafoState.nodoSeleccionado = null
                        GrafoState.rutaResaltada.clear()
                    }
                }

                "dijkstra" -> {
                    val origen = params["origen"]?.toIntOrNull()
                    val destino = params["destino"]?.toIntOrNull()
                    if (origen != null && destino != null) {
                        val oExists = GrafoState.nodos.any { it.id == origen }
                        val dExists = GrafoState.nodos.any { it.id == destino }
                        if (!oExists || !dExists) {
                            GrafoState.alert = "Uno o ambos IDs de nodo no existen."
                        } else {
                            ejecutarDijkstra(origen, destino)
                        }
                    }
                }
            }
        }

        val respuesta = GrafoState.toJson().toByteArray(Charsets.UTF_8)
        intercambio.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
        intercambio.sendResponseHeaders(200, respuesta.size.toLong())
        val os: OutputStream = intercambio.responseBody
        os.write(respuesta)
        os.close()
    }

    servidor.executor = null
    servidor.start()
    println("🌍 ¡Servidor web para Dijkstra iniciado con éxito!")
    println("👉 Abre tu navegador e ingresa a: http://localhost:8080")
}

fun ejecutarDijkstra(origen: Int, destino: Int) {
    GrafoState.logs.add("Calculando distancia desde Nodo $origen hasta Nodo $destino...")
    val distancias = mutableMapOf<Int, Double>()
    val previos = mutableMapOf<Int, Int?>()
    val noVisitados = GrafoState.nodos.map { it.id }.toMutableList()

    GrafoState.nodos.forEach { nodo ->
        distancias[nodo.id] = Double.POSITIVE_INFINITY
        previos[nodo.id] = null
    }
    distancias[origen] = 0.0

    while (noVisitados.isNotEmpty()) {
        noVisitados.sortBy { distancias[it] ?: Double.POSITIVE_INFINITY }
        val actual = noVisitados.removeAt(0)

        if (actual == destino || distancias[actual] == Double.POSITIVE_INFINITY) break

        val vecinos = mutableListOf<Pair<Int, Int>>()
        GrafoState.aristas.forEach { arista ->
            if (arista.desde == actual && noVisitados.contains(arista.hasta)) {
                vecinos.add(Pair(arista.hasta, arista.peso))
            } else if (arista.hasta == actual && noVisitados.contains(arista.desde)) {
                vecinos.add(Pair(arista.desde, arista.peso))
            }
        }

        for (v in vecinos) {
            val caminoAlternativo = (distancias[actual] ?: 0.0) + v.second
            if (caminoAlternativo < (distancias[v.first] ?: Double.POSITIVE_INFINITY)) {
                distancias[v.first] = caminoAlternativo
                previos[v.first] = actual
            }
        }
    }

    if (distancias[destino] == Double.POSITIVE_INFINITY) {
        GrafoState.logs.add("❌ No existe una ruta posible entre Nodo $origen y Nodo $destino.")
        GrafoState.rutaResaltada.clear()
        return
    }

    val camino = mutableListOf<Int>()
    var u: Int? = destino
    while (u != null) {
        camino.add(0, u)
        u = previos[u]
    }

    GrafoState.rutaResaltada.clear()
    for (i in 0 until camino.size - 1) {
        GrafoState.rutaResaltada.add(RutaSegmento(camino[i], camino[i + 1]))
    }

    GrafoState.logs.add("🎉 ¡Ruta encontrada!")
    GrafoState.logs.add("➡ Camino: ${camino.joinToString(" ➔ ")}")
    GrafoState.logs.add("📏 Distancia Total: ${distancias[destino]?.toInt()}")
}

fun parseQueryParams(query: String?): Map<String, String> {
    if (query == null) return emptyMap()
    return query.split("&").associate {
        val parts = it.split("=")
        val key = parts[0]
        val value = if (parts.size > 1) parts[1] else ""
        key to value
    }
}