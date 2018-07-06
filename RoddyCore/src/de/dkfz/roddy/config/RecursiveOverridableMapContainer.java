/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config;

import de.dkfz.roddy.tools.RoddyConversionHelperMethods;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Helps configurations to store overridable versions of configuration values and other things
 */
public class RecursiveOverridableMapContainer<K, V extends RecursiveOverridableMapContainer.Identifiable, P extends ContainerParent> {

    public interface Identifiable {
        String getID();
    }

    /**
     * A list of values in this container's configuration
     */
    protected final Map<K, V> values = new LinkedHashMap<>();

    private final P containerParent;

    private final String id;

    RecursiveOverridableMapContainer(P containerParent, String id) {
        this.containerParent = containerParent;
        this.id = id;
    }

    public P getContainerParent() {
        return containerParent;
    }

    public String getId() {
        return id;
    }

    public Map<K, V> getMap() {
        return values;
    }

    public List<K> getListOfMapsValues() {
        return new LinkedList<>(values.keySet());
    }

    public List<K> getListOfAllValueKeys() {
        List<K> allValues = new LinkedList<>();
        for (P parent : (List<P>) containerParent.getParents())
            allValues.addAll(parent.getContainer(id).getListOfAllValueKeys());

        allValues.addAll(values.keySet());
        return allValues;
    }

    /**
     * @return a map of value keys and evaluated values
     */
    public Map<K, V> getAllValues() {
        Map<K, V> elevatedValues = new LinkedHashMap<>();
        for (V value : getAllUnevaluatedValues().values()) {
            elevatedValues.put((K) value.getID(), temporarilyElevateValue(value));
        }
        return elevatedValues;
    }

    /**
     * @return a list of evaluated values
     */
    public List<V> getAllValuesAsList() {
        return new LinkedList<>(getAllValues().values());
    }

    protected Map<K, V> getAllUnevaluatedValues() {
        Map<K, V> allValues = new LinkedHashMap<>();
        for (P parent : (List<P>) containerParent.getParents()) {
            allValues.putAll(parent.getContainer(id).getAllUnevaluatedValues());
        }

        allValues.putAll(values);
        return allValues;
    }

    public List<V> getInheritanceList(K valueID) throws ConfigurationError {
        List<V> allValues = new LinkedList<>();

        if (getMap().containsKey(valueID))
            allValues.add(getValue(valueID));

        P containerParent = getContainerParent();

        if (containerParent == null)
            return allValues;

        for (Object _configuration : containerParent.getParents()) {
            ContainerParent configuration = (ContainerParent)_configuration;
            allValues.addAll(configuration.getContainer(this.id).getInheritanceList(valueID));
        }

        return allValues;
    }

    public boolean hasValue(K id) {
        return getListOfAllValueKeys().contains(id);
    }

    /**
     * When you have several configuration objects, e.g.
     * A -> B
     *
     * A contains values a and b, where a == abc and b has a reference to a b=${a}
     * B contains values a'  == def
     * If you query B for b, b holds a reference to configuration A
     * Therefore, b.toString() will result in b == abc and NOT in def as expected.
     *
     * By elevating a copy of b to B, everything will be fixed, as B can be the parent of b'
     *
     * This is true for ConfigurationValue, which rely on dependencies between themselves.
     *
     * In RecursiveOverridableMapContainerForConfigurationValues, we override the method and
     * copy the value and set the configuration reference to (in this example) B
     *
     * @param src
     * @return
     */
    protected V temporarilyElevateValue(V src) {
        return src;
    }

    /**
     * Does not check, if the value exists!
     * @param id
     * @return
     */
    protected V _getValueUnchecked(K id) {
        V cval = getAllValues().get(id);
        if(!values.containsKey(id))
            return temporarilyElevateValue(cval);

        return cval;
    }

    /**
     * @param id
     * @param defaultValue
     * @return the evaluated value for the id, if it exists, otherwise the defaultValue
     */
    public V getValue(K id, V defaultValue) {
        if (!hasValue(id))
            return defaultValue;
        return _getValueUnchecked(id);
    }

    /**
     * @throws ConfigurationError if a value is requested that does not exist.
     * @param id    Configuration value to return.
     */
    public V getValue(K id) throws ConfigurationError {
        if (!hasValue(id))
            throw new ConfigurationError("Value for '" + id.toString() + "' could not be found in containers", id.toString());
        return _getValueUnchecked(id);
    }

    public void putAll(Map<K, V> values) {
        if (values == null) return;
        this.values.putAll(values);
    }

    public void put(K key, V value) {
        this.values.put(key, value);
    }

    public void add(V value) {
        this.values.put((K) value.getID(), value);
    }

    public void addAll(List<V> all) {
        if(RoddyConversionHelperMethods.isNullOrEmpty(all)) return;
        for(V it : all) { add(it); }
    }

    public void append(V value) {
        add(value);
    }

    public void leftShift(V value) {
        add(value);
    }

    public int size() {
        return values.size();
    }

    public boolean is(String id) {
        return this.id.equals(id);
    }

    @Override
    public String toString() {
        return String.format("Rec map [%s] with %d values.", getId(), values.size());
    }
}
