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

    // Security & filters
    private List<String> allowedChatTypes = new ArrayList<>();
    private boolean adminOnly;
    private List<String> requiredRoles = new ArrayList<>();
    private List<String> allowedUsers = new ArrayList<>();
    private boolean silentSecurity;
    private int priority;

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

    public List<String> getAllowedChatTypes() {
        return allowedChatTypes;
    }

    public void setAllowedChatTypes(List<String> allowedChatTypes) {
        this.allowedChatTypes = allowedChatTypes != null ? new ArrayList<>(allowedChatTypes) : new ArrayList<>();
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public void setAdminOnly(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    public List<String> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles != null ? new ArrayList<>(requiredRoles) : new ArrayList<>();
    }

    public List<String> getAllowedUsers() {
        return allowedUsers;
    }

    public void setAllowedUsers(List<String> allowedUsers) {
        this.allowedUsers = allowedUsers != null ? new ArrayList<>(allowedUsers) : new ArrayList<>();
    }

    public boolean isSilentSecurity() {
        return silentSecurity;
    }

    public void setSilentSecurity(boolean silentSecurity) {
        this.silentSecurity = silentSecurity;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HandlerDescriptor that = (HandlerDescriptor) o;
        return interruptsState == that.interruptsState
                && adminOnly == that.adminOnly
                && silentSecurity == that.silentSecurity
                && priority == that.priority
                && type == that.type
                && Objects.equals(matchValue, that.matchValue)
                && Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(paramTypes, that.paramTypes)
                && Objects.equals(stateCondition, that.stateCondition)
                && Objects.equals(messageType, that.messageType)
                && Objects.equals(allowedChatTypes, that.allowedChatTypes)
                && Objects.equals(requiredRoles, that.requiredRoles)
                && Objects.equals(allowedUsers, that.allowedUsers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, matchValue, className, methodName, paramTypes, interruptsState, stateCondition,
                messageType, allowedChatTypes, adminOnly, requiredRoles, allowedUsers, silentSecurity, priority);
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
                ", allowedChatTypes=" + allowedChatTypes +
                ", adminOnly=" + adminOnly +
                ", requiredRoles=" + requiredRoles +
                ", allowedUsers=" + allowedUsers +
                ", silentSecurity=" + silentSecurity +
                ", priority=" + priority +
                '}';
    }
}
