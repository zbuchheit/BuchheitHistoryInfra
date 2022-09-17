package myproject;

import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.azurenative.documentdb.*;
import com.pulumi.azurenative.documentdb.enums.DatabaseAccountKind;
import com.pulumi.azurenative.documentdb.enums.DatabaseAccountOfferType;
import com.pulumi.azurenative.documentdb.inputs.*;
import com.pulumi.azurenative.documentdb.outputs.ListDatabaseAccountConnectionStringsResult;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.storage.StorageAccount;
import com.pulumi.azurenative.storage.StorageAccountArgs;
import com.pulumi.azurenative.storage.StorageFunctions;
import com.pulumi.azurenative.storage.enums.Kind;
import com.pulumi.azurenative.storage.enums.SkuName;
import com.pulumi.azurenative.storage.inputs.ListStorageAccountKeysArgs;
import com.pulumi.azurenative.documentdb.DocumentdbFunctions;
import com.pulumi.azurenative.storage.inputs.SkuArgs;
import com.pulumi.core.Either;
import com.pulumi.core.Output;
import com.pulumi.deployment.InvokeOptions;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.StackReference;

import java.util.List;

public class App {
    public static void main(String[] args) {
        Pulumi.run(App::stack);
    }

    private static void stack(Context ctx) {
        var config = ctx.config();
        var azConfig = ctx.config("azure-native");
        var environment = config.require("environment");
        var location = azConfig.require("location");
        var appName = config.require("appName");
        Output<String> resourceGroupName = (Output<String>) new StackReference(String.format("zbuchheit/BuchheitHistory-Core/%s", environment)).getOutput("resourceGroupName");

        var cosmosAccount = new DatabaseAccount("databaseAccount", DatabaseAccountArgs.builder()
                .accountName(String.format("cosmos-%s-%s", appName.toLowerCase(), location.toLowerCase()))
                .databaseAccountOfferType(DatabaseAccountOfferType.Standard)
                .enableFreeTier(true)
                .kind(DatabaseAccountKind.MongoDB)
                .location(location)
                .resourceGroupName(resourceGroupName)
                .locations(
                        LocationArgs.builder()
                                .locationName(location)
                                .failoverPriority(0)
                                .isZoneRedundant(false)
                                .build()
                )
                .build());

        var mongoDatabase = new MongoDBResourceMongoDBDatabase("mongoDatabase",
                MongoDBResourceMongoDBDatabaseArgs.builder()
                .accountName(cosmosAccount.name())
                .databaseName("BuchheitHistoryDb")
                .location(location)
                .resource(MongoDBDatabaseResourceArgs.builder().id("BuchheitHistoryDb").build())
                .resourceGroupName(resourceGroupName)
                .build()
        );

        var mongoDBCollection = new MongoDBResourceMongoDBCollection("mongoCollection",
                MongoDBResourceMongoDBCollectionArgs.builder()
                .accountName(cosmosAccount.name())
                .collectionName("individuals")
                .databaseName(mongoDatabase.name())
                .options(CreateUpdateOptionsArgs.builder()
                        .autoscaleSettings(AutoscaleSettingsArgs.builder().maxThroughput(1000).build())
                        .build())
                .resource(
                        MongoDBCollectionResourceArgs.builder()
                                .id("individuals")
                                .indexes(
                                        MongoIndexArgs.builder()
                                                .key(
                                                        MongoIndexKeysArgs.builder()
                                                                .keys("_id")
                                                                .build()
                                                )
                                                .build(),
                                        MongoIndexArgs.builder()
                                                .key(
                                                        MongoIndexKeysArgs.builder()
                                                                .keys("$**")
                                                                .build()
                                                )
                                                .build()
                                ).build()
                )
                .resourceGroupName(resourceGroupName)
                .build()
        );

        var authCollection = new MongoDBResourceMongoDBCollection("authCollection",
                MongoDBResourceMongoDBCollectionArgs.builder()
                .accountName(cosmosAccount.name())
                .collectionName("applicationUser")
                .databaseName(mongoDatabase.name())
                .options(
                        CreateUpdateOptionsArgs.builder()
                        .autoscaleSettings(
                                AutoscaleSettingsArgs.builder()
                                        .maxThroughput(1000)
                                        .build()
                        )
                        .build()
                )
                .resource(
                        MongoDBCollectionResourceArgs.builder()
                                .id("applicationUser")
                                .indexes(
                                        MongoIndexArgs.builder()
                                                .key(
                                                        MongoIndexKeysArgs.builder()
                                                                .keys("_id")
                                                                .build()
                                                )
                                                .build(),
                                        MongoIndexArgs.builder()
                                                .key(
                                                        MongoIndexKeysArgs.builder()
                                                                .keys("$**")
                                                                .build()
                                                )
                                                .build()
                                ).build()
                )
                .resourceGroupName(resourceGroupName)
                .build()
        );

        var databaseConnectionString = Output.tuple(resourceGroupName, cosmosAccount.name()).apply(
                t -> Output.of(
                        DocumentdbFunctions.listDatabaseAccountConnectionStrings(
                                ListDatabaseAccountConnectionStringsArgs.builder()
                                        .resourceGroupName(t.t1)
                                        .accountName(t.t2)
                                        .build()
                        )
                ).applyValue(response -> response.connectionStrings().get(0).connectionString())
        );
    ctx.export("databaseConnectionString", Output.ofSecret(databaseConnectionString));

    }
}
