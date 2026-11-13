package io.quarkiverse.telegram.bot.runtime.dispatcher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HandlerDescriptor implements Serializable {

    private HandlerType type;
    private String matchValue;
    private String className;
    private String methodName;
    private List<String> paramTypes = new ArrayList<>();
    private boolean interruptsState;
    private String stateCondition;
    private String messageType;
    private String description;

    public HandlerDescriptor() {
    }

    public HandlerDescriptor(
            HandlerType type,
            String matchValue,
            String className,
            String methodName,
            List<String> paramTypes,
            boolean interruptsState,
            String stateCondition,
            String messageType,
            String description) {
        this.type = type;
        this.matchValue = matchValue;
        this.className = className;
        this.methodName = methodName;
        this.paramTypes = paramTypes != null ? new ArrayList<>(paramTypes) : new ArrayList<>();
        this.interruptsState = interruptsState;
        this.stateCondition = stateCondition;
        this.messageType = messageType;
        this.description = description;
    }

    public HandlerDescriptor(
            HandlerType type,
            String matchValue,
            String className,
            String methodName,
            List<String> paramTypes,
            boolean interruptsState,
            String stateCondition,
            String messageType) {
        this(type, matchValue, className, methodName, paramTypes, interruptsState, stateCondition, messageType, null);
    }

    public HandlerDescriptor(
            HandlerType type,
            String matchValue,
            String className,
            String methodName,
            List<String> paramTypes) {
        this(type, matchValue, className, methodName, paramTypes, false, null, null);
    }

    public HandlerType getType() {
        return type;
    }

    public void setType(HandlerType type) {
        this.type = type;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public void setMatchValue(String matchValue) {
        this.matchValue = matchValue;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getParamTypes() {
        return paramTypes;
    }

    public void setParamTypes(List<String> paramTypes) {
        this.paramTypes = paramTypes != null ? new ArrayList<>(paramTypes) : new ArrayList<>();
    }

    public boolean isInterruptsState() {
        return interruptsState;
    }

    public void setInterruptsState(boolean interruptsState) {
        this.interruptsState = interruptsState;
    }

    public String getStateCondition() {
        return stateCondition;
    }

    public void setStateCondition(String stateCondition) {
        this.stateCondition = stateCondition;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HandlerDescriptor that = (HandlerDescriptor) o;
        return interruptsState == that.interruptsState
                && type == that.type
                && Objects.equals(matchValue, that.matchValue)
                && Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(paramTypes, that.paramTypes)
                && Objects.equals(stateCondition, that.stateCondition)
                && Objects.equals(messageType, that.messageType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, matchValue, className, methodName, paramTypes, interruptsState, stateCondition,
                messageType);
    }

    @Override
    public String toString() {
        return "HandlerDescriptor{" +
                "type=" + type +
                ", matchValue='" + matchValue + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", paramTypes=" + paramTypes +
                ", interruptsState=" + interruptsState +
                ", stateCondition='" + stateCondition + '\'' +
                ", messageType='" + messageType + '\'' +
                '}';
    }
}
