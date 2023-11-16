package be.kuleuven.distributedsystems.cloud.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class PubSub {

    TopicName topicName;
    SubscriptionName subscriptionName;
    TransportChannelProvider channelProvider;
    CredentialsProvider credentialsProvider;
    com.google.cloud.pubsub.v1.Publisher publisher;
    Topic topic;

    public PubSub(String projectId, String topicId, String subscriptionId) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8083").usePlaintext().build();
            channelProvider =
                    FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            credentialsProvider = NoCredentialsProvider.create();

            topicName = TopicName.of(projectId, topicId);
            subscriptionName = SubscriptionName.of(projectId, subscriptionId);
            createTopic();
            createPublisher();
            createSubscription("http://localhost:8080/subscription/confirmQuote");
    }

    public void createTopic() {
        try {
            TopicAdminClient topicClient =
                    TopicAdminClient.create(
                            TopicAdminSettings.newBuilder()
                                    .setTransportChannelProvider(channelProvider)
                                    .setCredentialsProvider(credentialsProvider)
                                    .build());
            topic = topicClient.createTopic(topicName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createPublisher() {
        try {
            publisher = com.google.cloud.pubsub.v1.Publisher
                    .newBuilder(topicName)
                    .setChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createSubscription(String pushEndpoint) {
        SubscriptionAdminSettings settings =
                null;
        try {
            settings = SubscriptionAdminSettings.newBuilder()
                    .setTransportChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(settings)) {
            PushConfig pushConfig = PushConfig.newBuilder()
                    .setPushEndpoint(pushEndpoint)
                    .build();
            subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 60);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String message, String userEmail) {
        ByteString data = ByteString.copyFromUtf8(message);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(data)
                .putAttributes("userEmail", userEmail)
                .build();
        ApiFuture<String> future = publisher.publish(pubsubMessage);
        String messageId = null;
        try {
            messageId = future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Published message ID: " + messageId);
    }
}
