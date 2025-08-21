package com.trading.model;

public enum OrderStates
{
    NEW_ORDER("New Order"),
    PENDING_NEW("Pending New Order"),
    ACCEPTED_BY_OMS("New Order Acknowledged"),
    REJECTED_BY_OMS("Rejected By OMS"),
    ACCEPTED_BY_DESK("Accepted By Desk"),
    REJECTED_BY_DESK("Rejected By Desk"),
    PENDING_EXCH("Sent to Exchange"),
    ACCEPTED_BY_EXCH("Acknowledged by Exchange"),
    REJECTED_BY_EXCH("Rejected By Exchange"),
    PENDING_CANCEL("Pending Cancel"),
    PENDING_REPLACE("Pending Replace"),
    REPLACED_BY_EXCH("Replaced by Exchange"),
    CANCELLED_BY_EXCH("Cancelled by Exchange"),
    PARTIALLY_FILLED("Partially Filled"),
    REPLACE_REJECTED_BY_EXCH("Replace Rejected by Exchange"),
    FULLY_FILLED("Fully Filled"),
    CANCEL_REJECTED_BY_EXCH("Cancel Rejected by Exchange"),
    CANCELLED_BY_DESK("Cancelled by Desk"),
    REPLACED_BY_DESK("Replaced by Desk"),
    DONE_FOR_DAY("Done For Day");

    private final String orderStateDescription;

    OrderStates(String orderStateDescription)
    {
        this.orderStateDescription = orderStateDescription;
    }

    public String getOrderStateDescription()
    {
        return orderStateDescription;
    }
}
