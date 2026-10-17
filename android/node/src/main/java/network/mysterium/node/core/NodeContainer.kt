package network.mysterium.node.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mysterium.MobileNode
import mysterium.Mysterium

//workaround to inject mobile node
class NodeContainer(private val context: Context) {

    private var mobileNode: MobileNode? = null

    private val mutex = Mutex()
    suspend fun getInstance(): MobileNode = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mobileNode != null) {
                mobileNode!!
            } else {
                mobileNode = Mysterium.newNode(
                    context.filesDir.canonicalPath,
                    Mysterium.defaultProviderNodeOptions()
                )
                mobileNode!!
            }
        }
    }

    /**
     * Drops the cached node instance after it has been shut down
     * ([MobileNode.shutdown]) so the next [getInstance] call creates a
     * fresh one. Without this, the app would keep handing out the dead
     * instance after a "Shut down node" and the embedded TequilAPI/NodeUI
     * (localhost:4449) would never come back.
     */
    fun reset() {
        mobileNode = null
    }
}
