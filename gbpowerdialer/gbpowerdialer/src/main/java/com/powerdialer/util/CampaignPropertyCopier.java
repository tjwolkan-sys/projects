package com.powerdialer.util;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.base.BaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Transient;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Utility class for copying properties between Campaign objects based on specific criteria.
 * This class uses reflection with extensive caching for optimal performance and addresses Fortify findings.
 */
public final class CampaignPropertyCopier {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignPropertyCopier.class);
    private static final Map<Class<?>, Map<Method, Method>> SETTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> GETTER_CACHE = new ConcurrentHashMap<>();
    private static final List<String> SPECIAL_PROPERTY_KEYWORDS = Arrays.asList(
            "ringing", "totallist", "actuallist", "callbacklist"
    );

    private CampaignPropertyCopier() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Preloads the cache for methods.
     * This method pre-resolves and caches getter/setter pairs that will be used by {@code copyProperties}.
     * Accessibility for both getters and setters is set once and then cached.
     *
     * @param campaignClass The Class object of the Campaign class.
     */
    public static void preloadCampaignMethods(Class<?> campaignClass) {
        LOG.info("Pre-loading CampaignPropertyCopier cache for class: {}", campaignClass.getName());
        Set<Method> allGetters = collectAllMethods(campaignClass);
        for (Method getter : allGetters) {
            if (isSimpleGetter(getter) && (TRANSIENT_FIELD_FILTER.test(getter) || PERSISTENT_FIELD_FILTER.test(getter))) {
                resolveGetter(campaignClass, getter.getName());
                resolveSetter(campaignClass, getter);
            }
        }
        LOG.info("CampaignPropertyCopier cache pre-loading completed for class: {}", campaignClass.getName());
    }

    /**
     * Copies a single collection from the source to the target.
     * If the source collection is not null, a new copy is created and assigned to the target.
     * This method ensures compile-time safety by using Method References.
     *
     * @param sourceGetter       A Supplier to get the collection from the source object (e.g., `source::getSkills`).
     * @param targetSetter       A Consumer to set the collection on the target object (e.g., `target::setSkills`).
     * @param collectionSupplier A Supplier to create a new empty collection instance (e.g., `HashSet::new` or `ArrayList::new`).
     * @param <T>                The type of elements within the collection.
     * @param <C>                The type of the collection to be copied (e.g., `Set<T>`, `List<T>`).
     */
    private static <T, C extends Collection<T>> void copyCollection(
            Supplier<Collection<T>> sourceGetter,
            Consumer<C> targetSetter,
            Supplier<C> collectionSupplier) {
        Collection<T> source = sourceGetter.get();
        if (source != null) {
            C copy = collectionSupplier.get();
            copy.addAll(source);
            targetSetter.accept(copy);
        }
    }

    /**
     * Copies shallow (collection-based) relationships from the source Campaign object to the target Campaign object.
     * This method creates a new copy of each collection and assigns it to the target.
     *
     * @param source The source Campaign object. Cannot be null.
     * @param target The target Campaign object. Cannot be null.
     */
    public static void copyShallowRelationships(Campaign source, Campaign target) {
        Objects.requireNonNull(source, "Source campaign cannot be null");
        Objects.requireNonNull(target, "Target campaign cannot be null");
        copySetInPlaceByIdReplace(
                target,
                source::getSkills,
                target::getSkills,
                target::setSkills,
                java.util.HashSet::new,
                (camp, e) -> {
                    if (e != null) {
                        try { e.getClass().getMethod("setCampaign", Campaign.class).invoke(e, camp); } catch (Exception ignore) {}
                    }
                }
        );

        copySetInPlaceByIdReplace(
                target,
                source::getLocations,
                target::getLocations,
                target::setLocations,
                java.util.HashSet::new,
                (camp, e) -> {
                    if (e != null) {
                        try { e.getClass().getMethod("setCampaign", Campaign.class).invoke(e, camp); } catch (Exception ignore) {}
                    }
                }
        );

        copySetInPlaceByIdReplace(
                target,
                source::getDncs,
                target::getDncs,
                target::setDncs,
                java.util.HashSet::new,
                (camp, e) -> {
                    if (e != null) {
                        try { e.getClass().getMethod("setCampaign", Campaign.class).invoke(e, camp); } catch (Exception ignore) {}
                    }
                }
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getLscs,
                target::getLscs,
                target::setLscs,
                java.util.ArrayList::new,
                (camp, e) -> {
                    if (e != null) {
                        try {
                            e.getClass().getMethod("setCampaign", Campaign.class).invoke(e, camp);
                        } catch (Exception ignore) {}
                    }
                },
                true
        );


        copyOneToManyInPlaceByIdReplace(
                target,
                source::getFilters,
                target::getFilters,
                target::setFilters,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getSchedules,
                target::getSchedules,
                target::setSchedules,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getBlackoutSchedules,
                target::getBlackoutSchedules,
                target::setBlackoutSchedules,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getCampaignGroups,
                target::getCampaignGroups,
                target::setCampaignGroups,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getPhoneNumberColumns,
                target::getPhoneNumberColumns,
                target::setPhoneNumberColumns,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getSortFields,
                target::getSortFields,
                target::setSortFields,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getResultCodeSchedules,
                target::getResultCodeSchedules,
                target::setResultCodeSchedules,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getAttachedDatas,
                target::getAttachedDatas,
                target::setAttachedDatas,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getPredefinedValues,
                target::getPredefinedValues,
                target::setPredefinedValues,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );

        copyOneToManyInPlaceByIdReplace(
                target,
                source::getScheduleAutomations,
                target::getScheduleAutomations,
                target::setScheduleAutomations,
                java.util.ArrayList::new,
                (camp, e) -> { if (e != null) e.setCampaign(camp); },
                true
        );


        LOG.info("Shallow relationships copied for Campaign ID: {}", target.getId());
    }


    private static <T> void copyOneToManyInPlaceByIdReplace(
            Campaign target,
            Supplier<List<T>> sourceGetter,
            Supplier<List<T>> targetGetter,
            Consumer<List<T>> targetSetter,
            Supplier<List<T>> newListSupplier,
            java.util.function.BiConsumer<Campaign, T> backRefSetter,
            boolean keepOrder
    ) {
        final List<T> source = sourceGetter.get();
        if (source == null) return;

        List<T> targetList = targetGetter.get();
        if (targetList == null) {
            targetList = newListSupplier.get();
            for (T s : source) {
                backRefSetter.accept(target, s);
                targetList.add(s);
            }
            targetSetter.accept(targetList);
            return;
        }

        final Map<Object, Integer> targetIndexById = new LinkedHashMap<>();
        for (int i = 0; i < targetList.size(); i++) {
            Object id = extractId(targetList.get(i));
            if (id != null) targetIndexById.put(id, i);
        }

        final Set<Object> sourceIds = new LinkedHashSet<>();
        for (T s : source) {
            Object id = extractId(s);
            if (id != null) sourceIds.add(id);
        }

        for (T s : source) {
            Object id = extractId(s);
            Integer idx = (id != null) ? targetIndexById.get(id) : null;
            backRefSetter.accept(target, s);
            if (idx != null) {
                targetList.set(idx, s);
            } else {
                targetList.add(s);
                if (id != null) targetIndexById.put(id, targetList.size() - 1);
            }
        }

        targetList.removeIf(t -> {
            Object id = extractId(t);
            return id != null && !sourceIds.contains(id);
        });

        if (keepOrder) {
            final Map<Object, Integer> order = new HashMap<>();
            int i = 0;
            for (T s : source) {
                Object id = extractId(s);
                if (id != null) order.put(id, i++);
            }
            targetList.sort(Comparator.comparingInt(t -> order.getOrDefault(extractId(t), Integer.MAX_VALUE)));
        }
    }

    private static Object extractId(Object entity) {
        if (entity == null) return null;
        try {
            if (entity instanceof org.hibernate.proxy.HibernateProxy) {
                return ((org.hibernate.proxy.HibernateProxy) entity)
                        .getHibernateLazyInitializer()
                        .getIdentifier();
            }
            Method m = entity.getClass().getMethod("getId");
            m.setAccessible(true);
            return m.invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }


    private static <T> void copySetInPlaceByIdReplace(
            Campaign target,
            Supplier<Set<T>> sourceGetter,
            Supplier<Set<T>> targetGetter,
            Consumer<Set<T>> targetSetter,
            Supplier<Set<T>> newSetSupplier,
            java.util.function.BiConsumer<Campaign, T> backRefSetter
    ) {

        final Set<T> source = sourceGetter.get();
        if (source == null) return;

        Set<T> targetSet = targetGetter.get();
        if (targetSet == null) {
            targetSet = newSetSupplier.get();
            for (T s : source) {
                backRefSetter.accept(target, s);
                targetSet.add(s);
            }
            targetSetter.accept(targetSet);
            return;
        }

        final Map<Object, T> targetById = new LinkedHashMap<>();
        for (T t : targetSet) {
            Object id = extractId(t);
            if (id != null) targetById.put(id, t);
        }

        final Set<Object> sourceIds = new LinkedHashSet<>();
        for (T s : source) {
            Object id = extractId(s);
            if (id != null) sourceIds.add(id);
        }

        Set<T> newSet = newSetSupplier.get();
        for (T s : source) {
            Object id = extractId(s);
            T existing = (id != null) ? targetById.get(id) : null;
            backRefSetter.accept(target, s);
            if (existing != null) {
                newSet.add(s);
            } else {
                newSet.add(s);
            }
        }

        targetSet.clear();
        targetSet.addAll(newSet);
    }



    private static boolean isShouldSkipCopy(String propertyName, Object sourceValue, Object targetCurrentValue) {
        String name = propertyName.toLowerCase();

        boolean isSpecialProperty = false;
        for (String keyword : SPECIAL_PROPERTY_KEYWORDS) {
            if (name.contains(keyword)) {
                isSpecialProperty = true;
                break;
            }
        }

        if (isSpecialProperty) {
            boolean sourceIsZeroOrNull = (sourceValue == null) ||
                    (sourceValue instanceof Number && ((Number) sourceValue).longValue() == 0) ||
                    (sourceValue instanceof AtomicInteger && ((AtomicInteger) sourceValue).get() == 0) ||
                    (sourceValue instanceof AtomicLong && ((AtomicLong) sourceValue).get() == 0);

            boolean targetHasValue = (targetCurrentValue != null) &&
                    ((targetCurrentValue instanceof Number && ((Number) targetCurrentValue).longValue() != 0) ||
                            (targetCurrentValue instanceof AtomicInteger && ((AtomicInteger) targetCurrentValue).get() != 0) ||
                            (targetCurrentValue instanceof AtomicLong && ((AtomicLong) targetCurrentValue).get() != 0));

            return sourceIsZeroOrNull && targetHasValue;
        }

        if (sourceValue == null && targetCurrentValue != null) {
            return true;
        }

        return false;
    }

    /**
     * Copies properties from source to target based on a given filter.
     * This method handles simple properties (primitives, wrappers, String, Date, Enum, Numbers, Booleans)
     * and atomic number types. For collection-based relationships, use {@link #copyShallowRelationships(Campaign, Campaign)}.
     *
     * @param source The source Campaign object. Must not be null.
     * @param target The target Campaign object. Must not be null.
     * @param filter A predicate to determine which getter methods (and thus properties) to copy. Must not be null.
     */
    public static void copyProperties(Campaign source, Campaign target, Predicate<Method> filter) {
        Objects.requireNonNull(source, "Source campaign cannot be null");
        Objects.requireNonNull(target, "Target campaign cannot be null");
        Objects.requireNonNull(filter, "Filter predicate cannot be null");

        AtomicInteger copied = new AtomicInteger(0);
        AtomicInteger skippedNoSetter = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skippedExistingValue = new AtomicInteger(0);

        Set<Method> allGetters = collectAllMethods(source.getClass());

        allGetters.stream()
                .filter(getter -> isSimpleGetter(getter) && filter.test(getter))
                .forEach(getter -> {
                    Method cachedGetter = resolveGetter(source.getClass(), getter.getName());
                    if (cachedGetter == null) {
                        LOG.warn("Getter method '{}' not found in cache for source class (or not public). Skipping property.", getter.getName());
                        skippedNoSetter.incrementAndGet();
                        return;
                    }

                    Method setter = resolveSetter(target.getClass(), getter);
                    if (setter == null) {
                        skippedNoSetter.incrementAndGet();
                        LOG.trace("No compatible setter found for getter: {}", getter.getName());
                        return;
                    }

                    try {
                        Object sourceValue = cachedGetter.invoke(source);
                        Object targetCurrentValue = cachedGetter.invoke(target);


                        if (isShouldSkipCopy(getter.getName(), sourceValue, targetCurrentValue)) {
                            LOG.debug("Skipping copy for property '{}' as target already has a value and source is null/zero.", getter.getName());
                            skippedExistingValue.incrementAndGet();
                            return;
                        }

                        copySingleProperty(source, target, cachedGetter, setter, copied, failed);
                    } catch (ReflectiveOperationException e) {
                        failed.incrementAndGet();
                        LOG.warn("Pre-copy check failed for {}: {}", getter.getName(), e.getMessage());
                    }
                });

        LOG.info("Property copy completed → Copied: {}, Skipped(noSetter): {}, Skipped(existingValue): {}, Failed: {}, Target: {}",
                copied.get(), skippedNoSetter.get(), skippedExistingValue.get(), failed.get(), target.getId());
    }

    private static Long extractIdWithoutInitializing(Object rel) {
        if (rel == null) return null;
        try {
            if (rel instanceof org.hibernate.proxy.HibernateProxy) {
                return (Long) ((org.hibernate.proxy.HibernateProxy) rel)
                        .getHibernateLazyInitializer()
                        .getIdentifier();
            }
            try {
                Method m = rel.getClass().getMethod("getId");
                m.setAccessible(true);
                Object idObj = m.invoke(rel);
                return (idObj instanceof Long) ? (Long) idObj : null;
            } catch (NoSuchMethodException ignore) {
                return null;
            }
        } catch (Exception e) {
            LOG.debug("extractIdWithoutInitializing failed for {}: {}", rel, e.getMessage());
            return null;
        }
    }

    public static void copyEntityReferences(
            Campaign source,
            Campaign target,
            EntityRefResolver resolver,
            Set<Class<?>> allowedRelationTypes
    ) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(resolver);

        for (Method getter : collectAllMethods(source.getClass())) {
            if (!isSimpleGetter(getter)) continue;

            Class<?> rt = getter.getReturnType();
            if (!allowedRelationTypes.contains(rt)) continue;

            Method setter = resolveSetter(target.getClass(), getter);
            if (setter == null) continue;

            try {
                Object rel = getter.invoke(source);
                if (rel == null) continue;

                Long id = extractIdWithoutInitializing(rel);
                if (id == null) continue;

                Object ref = resolver.ref(rt, id);
                setter.invoke(target, ref);
            } catch (ReflectiveOperationException e) {
                LOG.warn("copyEntityReferences failed for {}: {}", getter.getName(), e.getMessage());
            }
        }
    }


    /**
     * Copies a single property from the source object to the target object.
     * This method encapsulates the core logic for property copying, including reflection invocation and error handling.
     *
     * @param source The source object.
     * @param target The target object.
     * @param getter The getter method for the property to be copied. (Its accessibility should already be set by {@link #resolveGetter})
     * @param setter The setter method for the property to be set. (Its accessibility should already be set by {@link #resolveSetter})
     * @param copied An AtomicInteger to track the count of successfully copied properties.
     * @param failed An AtomicInteger to track the count of failed property copies.
     */
    private static void copySingleProperty(Object source, Object target, Method getter, Method setter, AtomicInteger copied, AtomicInteger failed) {
        try {
            Object value = getter.invoke(source);
            Object arg = adaptValueForSetter(value, setter.getParameterTypes()[0]);

            setter.invoke(target, arg);
            copied.incrementAndGet();

            if (LOG.isTraceEnabled() && getter.getName().contains("getAgentsSize")) { // Keep specific logging for critical fields
                LOG.trace("{} campaign: {} copied with value {}", target, getter.getName(), value);
            }

        } catch (ReflectiveOperationException ex) {
            failed.incrementAndGet();
            LOG.warn("Property copy failed for {}: {}", getter.getName(), ex.getMessage());
        }
    }


    /**
     * Checks if a given method is a simple getter (starts with "get" or "is", has no parameters, and returns a value).
     *
     * @param m The method to check.
     * @return true if the method is a simple getter, false otherwise.
     */
    private static boolean isSimpleGetter(Method m) {
        return m.getParameterCount() == 0 &&
                (m.getName().startsWith("get") || m.getName().startsWith("is")) &&
                !void.class.equals(m.getReturnType());
    }

    /**
     * Collects all declared methods (including inherited ones) from a given class and its superclasses.
     *
     * @param type The class to collect methods from.
     * @return A Set of Method objects.
     */
    private static Set<Method> collectAllMethods(Class<?> type) {
        Set<Method> set = new HashSet<>();
        Class<?> currentClass = type;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            set.addAll(Arrays.asList(currentClass.getDeclaredMethods()));
            currentClass = currentClass.getSuperclass();
        }
        return set;
    }

    /**
     * Resolves and caches the compatible setter method for a given getter method on a target class.
     * Uses a ConcurrentHashMap for thread-safe caching. The setter's accessibility is set once and cached.
     *
     * @param targetCls The class on which to find the setter.
     * @param getter    The getter method for which to find a corresponding setter.
     * @return The compatible setter method, or null if not found.
     */
    private static Method resolveSetter(Class<?> targetCls, Method getter) {
        return SETTER_CACHE
                .computeIfAbsent(targetCls, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(getter, g -> {
                    String setterName = toSetterName(g.getName());
                    Method s = findCompatibleSetter(targetCls, setterName, g.getReturnType());
                    if (s != null) s.setAccessible(true);
                    return s;
                });
    }

    /**
     * New: Resolves, makes accessible, and caches the getter method for a given class and getter name.
     * The getter's accessibility is set once and then cached.
     *
     * @param cls        The class in which to find the method.
     * @param getterName The name of the getter method (e.g., "getSkills").
     * @return The found and made-accessible Method object, or null.
     */
    private static Method resolveGetter(Class<?> cls, String getterName) {
        return GETTER_CACHE
                .computeIfAbsent(cls, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(getterName, name -> {
                    try {
                        Method getter = cls.getMethod(name);
                        if (getter != null) {
                            getter.setAccessible(true);
                        }
                        return getter;
                    } catch (NoSuchMethodException e) {
                        LOG.trace("Getter method '{}' not found in class {}.", name, cls.getName());
                        return null;
                    }
                });
    }


    /**
     * Converts a getter method name (e.g., "getSkills", "isReady") to its corresponding setter name (e.g., "setSkills", "setReady").
     *
     * @param getter The getter method name.
     * @return The corresponding setter method name.
     */
    private static String toSetterName(String getter) {
        return getter.startsWith("is") ? "set" + getter.substring(2) : "set" + getter.substring(3);
    }

    /**
     * Finds a compatible setter method in a class based on its name and the expected parameter type.
     * Includes logic for common type adaptations (e.g., AtomicInteger to int).
     *
     * @param cls        The class to search for the setter.
     * @param setterName The name of the setter method.
     * @param getterType The return type of the corresponding getter (which dictates the setter's parameter type).
     * @return The found Method object, or null if no compatible setter is found.
     */
    private static Method findCompatibleSetter(Class<?> cls, String setterName, Class<?> getterType) {
        Method m = findByName(cls, setterName, getterType);
        if (m != null) return m;

        // Search for compatible setters for special type conversions
        if (getterType == AtomicInteger.class) return findByName(cls, setterName, int.class, Integer.class);
        if (getterType == AtomicLong.class) return findByName(cls, setterName, long.class, Long.class);
        if (getterType == Integer.class || getterType == int.class)
            return findByName(cls, setterName, AtomicInteger.class);
        if (getterType == Long.class || getterType == long.class) return findByName(cls, setterName, AtomicLong.class);
        if (getterType == Boolean.class) return findByName(cls, setterName, boolean.class);
        if (getterType == double.class) return findByName(cls, setterName, Double.class);
        if (getterType == float.class) return findByName(cls, setterName, Float.class);

        // General compatibility check for collections
        if (Collection.class.isAssignableFrom(getterType)) {
            return Arrays.stream(cls.getDeclaredMethods())
                    .filter(meth -> meth.getName().equals(setterName) && meth.getParameterCount() == 1 &&
                            Collection.class.isAssignableFrom(meth.getParameterTypes()[0]))
                    .findFirst()
                    .orElse(null);
        }

        return Arrays.stream(cls.getDeclaredMethods())
                .filter(meth -> meth.getName().equals(setterName) && meth.getParameterCount() == 1)
                .findFirst()
                .orElse(null);
    }

    /**
     * Attempts to find a declared method by name and a list of possible parameter types.
     *
     * @param cls        The class to search.
     * @param name       The name of the method.
     * @param paramTypes An array of possible parameter types to try.
     * @return The found Method, or null if not found.
     */
    private static Method findByName(Class<?> cls, String name, Class<?>... paramTypes) {
        for (Class<?> pt : paramTypes) {
            try {
                return cls.getDeclaredMethod(name, pt);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Adapts a source value to a target parameter type, handling common conversions.
     * This is useful for converting between primitive/wrapper types and Atomic types.
     *
     * @param value           The value to adapt.
     * @param targetParamType The target parameter type of the setter.
     * @return The adapted value, or the original value if no adaptation is needed.
     */
    public static Object adaptValueForSetter(Object value, Class<?> targetParamType) {
        if (value == null) return null;

        if (value instanceof AtomicInteger) {
            int v = ((AtomicInteger) value).get();
            return (targetParamType == AtomicInteger.class) ? new AtomicInteger(v) : v;
        }
        if (value instanceof Integer) {
            Integer v = (Integer) value;
            return (targetParamType == AtomicInteger.class) ? new AtomicInteger(v) : v;
        }

        if (value instanceof AtomicLong) {
            long v = ((AtomicLong) value).get();
            return (targetParamType == AtomicLong.class) ? new AtomicLong(v) : v;
        }
        if (value instanceof Long) {
            Long v = (Long) value;
            return (targetParamType == AtomicLong.class) ? new AtomicLong(v) : v;
        }

        if (value instanceof Boolean) {
            Boolean b = (Boolean) value;
            return (targetParamType == boolean.class) ? b.booleanValue() : b;
        }

        if (value instanceof Double) {
            Double d = (Double) value;
            return (targetParamType == double.class) ? d.doubleValue() : d;
        }
        if (value instanceof Float) {
            Float f = (Float) value;
            return (targetParamType == float.class) ? f.floatValue() : f;
        }

        if (value instanceof Collection && Collection.class.isAssignableFrom(targetParamType)) {
            if (targetParamType.isAssignableFrom(HashSet.class)) {
                return new HashSet<>((Collection<?>) value);
            } else if (targetParamType.isAssignableFrom(ArrayList.class)) {
                return new ArrayList<>((Collection<?>) value);
            }
            return new ArrayList<>((Collection<?>) value);
        }

        return value;
    }

    /**
     * Predicate for copying @Transient annotated properties.
     * Excludes collections and maps, as they are handled separately by copyShallowRelationships or deep copy mechanisms.
     */
    public static final Predicate<Method> TRANSIENT_FIELD_FILTER = m -> {
        if (!m.isAnnotationPresent(Transient.class)) return false;
        Class<?> rt = m.getReturnType();

        if (rt.equals(AtomicInteger.class) || rt.equals(AtomicLong.class)) return false;

        if (Collection.class.isAssignableFrom(rt) || Map.class.isAssignableFrom(rt)) return false;

        if (rt.getName().contains("Cache")) return false;

        if (BaseEntity.class.isAssignableFrom(rt) && !rt.equals(Campaign.class)) return false;

        return true;
    };

    /**
     * Predicate for copying non-@Transient (persistent) simple properties.
     * Excludes collections, maps, and other related entities.
     */
    public static final Predicate<Method> PERSISTENT_FIELD_FILTER = m -> {
        if (m.isAnnotationPresent(Transient.class)) return false;
        Class<?> rt = m.getReturnType();
        if (Collection.class.isAssignableFrom(rt) || Map.class.isAssignableFrom(rt)) return false;
        if (BaseEntity.class.isAssignableFrom(rt) && !rt.equals(Campaign.class)) return false;
        if (rt.getName().contains("Cache")) return false;
        if (rt.equals(AtomicInteger.class) || rt.equals(AtomicLong.class)) return false;

        return rt.isPrimitive() || rt.equals(String.class) || rt.equals(Date.class) || rt.isEnum() ||
                Number.class.isAssignableFrom(rt) || Boolean.class.isAssignableFrom(rt);
    };
}