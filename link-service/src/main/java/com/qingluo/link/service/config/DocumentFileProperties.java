package com.qingluo.link.service.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tolink.document-file")
public class DocumentFileProperties {

    private Set<String> allowedSuffixes = DocumentFileTypeContract.supportedSuffixes();
    private long maxSizeBytes = 20L * 1024 * 1024;
    private long hardMaxSizeBytes = 100L * 1024 * 1024;
    private String internalBaseUrl = "http://localhost:8080";
    private String serviceToken;
    private boolean markdownAssetsEnabled = true;
    private long markdownAssetMaxBytes = 20L * 1024 * 1024;
    private int markdownAssetMaxCount = 200;
    private int markdownInventoryMaxCount = 5000;
    private long markdownBundleMaxBytes = 80L * 1024 * 1024;
    private int markdownAssetPathMaxLength = 512;
    private int markdownDocumentPathMaxLength = 255;
    private long zipMaxCompressedBytes = 100L * 1024 * 1024;
    private int zipMaxEntries = 5000;
    private long zipMaxExpandedBytes = 500L * 1024 * 1024;
    private int zipMaxRatio = 100;
    private int zipMaxDepth = 20;

    public Set<String> getAllowedSuffixes() {
        return DocumentFileTypeContract.resolveDeploymentSuffixes(allowedSuffixes);
    }

    public void setAllowedSuffixes(Set<String> allowedSuffixes) {
        this.allowedSuffixes = allowedSuffixes;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public long getHardMaxSizeBytes() {
        return hardMaxSizeBytes;
    }

    public void setHardMaxSizeBytes(long hardMaxSizeBytes) {
        this.hardMaxSizeBytes = hardMaxSizeBytes;
    }

    public String getInternalBaseUrl() {
        return internalBaseUrl;
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
        this.internalBaseUrl = internalBaseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public boolean isMarkdownAssetsEnabled() {
        return markdownAssetsEnabled;
    }

    public void setMarkdownAssetsEnabled(boolean markdownAssetsEnabled) {
        this.markdownAssetsEnabled = markdownAssetsEnabled;
    }

    public long getMarkdownAssetMaxBytes() {
        return markdownAssetMaxBytes;
    }

    public void setMarkdownAssetMaxBytes(long markdownAssetMaxBytes) {
        this.markdownAssetMaxBytes = markdownAssetMaxBytes;
    }

    public int getMarkdownAssetMaxCount() {
        return markdownAssetMaxCount;
    }

    public void setMarkdownAssetMaxCount(int markdownAssetMaxCount) {
        this.markdownAssetMaxCount = markdownAssetMaxCount;
    }

    public int getMarkdownInventoryMaxCount() {
        return markdownInventoryMaxCount;
    }

    public void setMarkdownInventoryMaxCount(int markdownInventoryMaxCount) {
        this.markdownInventoryMaxCount = markdownInventoryMaxCount;
    }

    public long getMarkdownBundleMaxBytes() {
        return markdownBundleMaxBytes;
    }

    public void setMarkdownBundleMaxBytes(long markdownBundleMaxBytes) {
        this.markdownBundleMaxBytes = markdownBundleMaxBytes;
    }

    public int getMarkdownAssetPathMaxLength() {
        return markdownAssetPathMaxLength;
    }

    public void setMarkdownAssetPathMaxLength(int markdownAssetPathMaxLength) {
        this.markdownAssetPathMaxLength = markdownAssetPathMaxLength;
    }

    public int getMarkdownDocumentPathMaxLength() {
        return markdownDocumentPathMaxLength;
    }

    public void setMarkdownDocumentPathMaxLength(int markdownDocumentPathMaxLength) {
        this.markdownDocumentPathMaxLength = markdownDocumentPathMaxLength;
    }

    public long getZipMaxCompressedBytes() {
        return zipMaxCompressedBytes;
    }

    public void setZipMaxCompressedBytes(long zipMaxCompressedBytes) {
        this.zipMaxCompressedBytes = zipMaxCompressedBytes;
    }

    public int getZipMaxEntries() {
        return zipMaxEntries;
    }

    public void setZipMaxEntries(int zipMaxEntries) {
        this.zipMaxEntries = zipMaxEntries;
    }

    public long getZipMaxExpandedBytes() {
        return zipMaxExpandedBytes;
    }

    public void setZipMaxExpandedBytes(long zipMaxExpandedBytes) {
        this.zipMaxExpandedBytes = zipMaxExpandedBytes;
    }

    public int getZipMaxRatio() {
        return zipMaxRatio;
    }

    public void setZipMaxRatio(int zipMaxRatio) {
        this.zipMaxRatio = zipMaxRatio;
    }

    public int getZipMaxDepth() {
        return zipMaxDepth;
    }

    public void setZipMaxDepth(int zipMaxDepth) {
        this.zipMaxDepth = zipMaxDepth;
    }
}
