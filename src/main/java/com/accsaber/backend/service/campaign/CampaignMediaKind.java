package com.accsaber.backend.service.campaign;

public enum CampaignMediaKind {

    BACKGROUND("campaigns", false),
    ICON("campaign-icons", false),
    CHECKPOINT_AVATAR("campaign-checkpoints", true),
    NODE_BORDER("campaign-node-borders", true);

    private final String subdir;
    private final boolean node;

    CampaignMediaKind(String subdir, boolean node) {
        this.subdir = subdir;
        this.node = node;
    }

    String subdir() {
        return subdir;
    }

    boolean node() {
        return node;
    }
}
