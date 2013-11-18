(ns com.okl.tokenmgr.storage
  )

(defprotocol StorageContainer
  (get-children [this path])
  (get-item [this path])
  (delete [this path])
  (create [this path data autocreate-message])
  (subtree [this path]))
