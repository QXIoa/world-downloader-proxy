package core.gui.jewel

import core.auth.RealmsApiHandler

/**
 * UI representation of a Minecraft Realm.
 */
class RealmInfo(
    val name: String,
    val motd: String,
    var address: String?,
    var loading: Boolean,
    val id: Int = -1,
    private val api: RealmsApiHandler? = null,
) {
    private var requestsLeft = 4

    fun requestIp() {
        val handler = api ?: return
        if (id < 0) return
        loading = true
        makeIpRequest(handler)
    }

    private fun makeIpRequest(handler: RealmsApiHandler) {
        requestsLeft--
        if (requestsLeft <= 0) {
            loading = false
            return
        }

        handler.requestRealmIp(id) { res ->
            try {
                if (!res.contains("Retry again later") && res.isNotEmpty()) {
                    val gson = com.google.gson.Gson()
                    val addr = gson.fromJson(res, RealmAddress::class.java)
                    if (addr?.address != null) {
                        address = addr.address
                        loading = false
                        return@requestRealmIp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Retry after delay
            Thread.sleep(4000)
            makeIpRequest(handler)
        }
    }
}

private data class RealmAddress(val address: String? = null)
