package org.shirolang.interpreter.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstract class contain shared code between function definitions
 * and function calls
 */
public abstract class Function implements Expression {
    protected String option;
    protected ArgumentsType argsType;
    protected Map<String, Expression> argMap;
    protected List<Expression> argList;

    public Function(String option) {
        this.option = option;
        this.argsType = ArgumentsType.LIST;
        this.argMap = new TreeMap<>();
        this.argList = new ArrayList<>();
    }

    public Function(String option, Map<String, Expression> argMap) {
        this(option);
        argsType = ArgumentsType.KEYWORDS;
        this.argMap.putAll(argMap);
    }

    public Function(String option, List<Expression> argList) {
        this(option);
        this.argList.addAll(argList);
    }

    public boolean hasActiveOption() {
        return !option.isEmpty();
    }

    public String getOption() {
        return option;
    }

    public ArgumentsType getArgsType() {
        return argsType;
    }

    public boolean hasKeywordArgs(){
        return argsType.equals(ArgumentsType.KEYWORDS);
    }

    public boolean hasListArgs(){
        return argsType.equals(ArgumentsType.LIST);
    }

    public boolean hasArgsDefined() {
        return !argList.isEmpty() || !argMap.isEmpty();
    }

    public List<Expression> getArgList(){
        return argList;
    }

    public Map<String, Expression> getArgMap() {
        return argMap;
    }

    public enum ArgumentsType{
        LIST, KEYWORDS
    }
}
