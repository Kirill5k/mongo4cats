package mongo4cats

import com.mongodb.client.model.{CreateCollectionOptions => JCreateCollectionOptions}

package object database {

  type CreateCollectionOptions = JCreateCollectionOptions
  object CreateCollectionOptions {
    def apply(): CreateCollectionOptions = new JCreateCollectionOptions()
  }
}
