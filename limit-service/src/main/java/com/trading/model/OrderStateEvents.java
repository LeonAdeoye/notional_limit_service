package com.trading.model;

public enum OrderStateEvents {
    SUBMIT_TO_OMS("Submit To OMS"),
    OMS_ACCEPT("OMS Accept"),
    OMS_REJECT("OMS Reject"),
    DESK_APPROVE("Desk Approve"),
    DESK_REJECT("Desk Reject"),
    SUBMIT_TO_EXCH("Submit To Exchange"),
    EXCH_APPROVE("Exchange Acknowledge"),
    EXCH_REJECT("Exchange Reject"),
    FULL_FILL("Full Fill"),
    DESK_CANCEL("Desk Cancel"),
    PARTIAL_FILL("Partial Fill"),
    DESK_REPLACE("Desk Replace"),
    DESK_DONE("Desk Done For Day");

    private final String orderStateEventDescription;

    OrderStateEvents(String orderStateEventDescription)
    {
        this.orderStateEventDescription = orderStateEventDescription;
    }

    public String getOrderStateEventDescription()
    {
        return orderStateEventDescription;
    }
}
