package com.appdynamics.monitors.azure.statsCollector;

import com.appdynamics.monitors.azure.request.AzureHttpsClient;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class BlobStatsCollector extends StorageStatsCollector {

    private static final Logger LOG = Logger.getLogger(BlobStatsCollector.class);

    private static final String CONTAINERS_REST = "https://%s.blob.core.windows.net/?comp=list";
    private static final String BLOB_REST = "https://%s.blob.core.windows.net/%s?restype=container&comp=list";

    private static final String METRIC_PATH = "Storage|Blob|%s|Container|%s|Blobs|%s|";

    public static final String STORAGE_ACCOUNT_NAMES_FOR_BLOB_KEY = "STORAGE_ACCOUNT_NAMES_FOR_BLOB";

    public BlobStatsCollector(AzureHttpsClient azureHttpsClient) {
        super(azureHttpsClient);
    }

    @Override
    public Map<String, Number> collectStats(String keyStorePath, String keyStorePassword, String subscriptionId, String restApiVersion, Properties displayProperties) {

        String storageAccountNamesForBlobString = displayProperties.getProperty(STORAGE_ACCOUNT_NAMES_FOR_BLOB_KEY);

        if (storageAccountNamesForBlobString == null || storageAccountNamesForBlobString.length() == 0) {
            LOG.error("No storage account name(s) defined for " + STORAGE_ACCOUNT_NAMES_FOR_BLOB_KEY + ". To show stats add them in metrics.property");
            return null;
        }

        String[] storageAccountNamesForBlob = storageAccountNamesForBlobString.split(",");

        List<String> storageAccountNamesForBlobList = Arrays.asList(storageAccountNamesForBlob);

        Map<String, String> storageAccountNamesWithKey = getStorageAccountNamesWithKey(keyStorePath, keyStorePassword, subscriptionId, restApiVersion, storageAccountNamesForBlobList);
        Map<String, Number> blobStatsMap = new LinkedHashMap<String, Number>();

        for (Map.Entry<String, String> storageAccountNameKey : storageAccountNamesWithKey.entrySet()) {
            String containerURL = String.format(CONTAINERS_REST, storageAccountNameKey.getKey());

            try {
                Document document = azureHttpsClient.processRequestWithHeadersForBlobQueue(containerURL, storageAccountNameKey.getKey(), storageAccountNameKey.getValue());
                NodeList containersNodeList = document.getElementsByTagName("Container");

                for (int i = 0; i < containersNodeList.getLength(); i++) {
                    Element element = (Element) containersNodeList.item(i);
                    String containerName = element.getElementsByTagName("Name").item(0).getTextContent();

                    String blobURL = String.format(BLOB_REST, storageAccountNameKey.getKey(), containerName);

                    try {
                        Document blobDocument = azureHttpsClient.processRequestWithHeadersForBlobQueue(blobURL, storageAccountNameKey.getKey(), storageAccountNameKey.getValue());
                        NodeList blobNodeList = blobDocument.getElementsByTagName("Blob");

                        for (int j = 0; j < blobNodeList.getLength(); j++) {
                            Element blobElement = (Element) blobNodeList.item(j);
                            String blobName = blobElement.getElementsByTagName("Name").item(0).getTextContent();
                            NodeList properties = blobElement.getElementsByTagName("Properties");
                            String blobSize = ((Element) properties.item(0)).getElementsByTagName("Content-Length").item(0).getTextContent();

                            Long size = null;
                            try {
                                size = Long.parseLong(blobSize);
                                blobStatsMap.put(String.format(METRIC_PATH, storageAccountNameKey.getKey(), containerName, blobName) + "Size", size);
                            } catch (NumberFormatException nfe) {
                                LOG.error("Unable to parse blob size " + blobSize + " of blob " + blobName);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("Unable to process response", e);
                    }
                }
            } catch (Exception e) {
                LOG.error("Unable to process response", e);
            }
        }
        return blobStatsMap;
    }
}
