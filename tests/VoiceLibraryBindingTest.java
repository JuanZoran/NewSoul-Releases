package dev.local.newsoul;

/** Field names in JADX Kotlin output are not necessarily runtime field names. */
public final class VoiceLibraryBindingTest {
    static class Renamed { private final String z9 = "conversation"; static String ignored = "static"; }
    static class Inherited extends Renamed { int unrelated; }
    static class Ambiguous { String a, b; }
    static class InheritedAmbiguous extends Renamed { String second; }
    static class Missing { Integer value; }
    static class Empty { String nullable; }
    public static void main(String[] args) throws Exception {
        if (!"conversation".equals(HostBindings.uniqueField(Renamed.class, String.class).get(new Renamed()))) throw new AssertionError();
        if (!"conversation".equals(HostBindings.uniqueField(Inherited.class, String.class).get(new Inherited()))) throw new AssertionError();
        for (Class<?> type : new Class<?>[]{Ambiguous.class, Missing.class, InheritedAmbiguous.class}) {
            try { HostBindings.uniqueField(type, String.class); throw new AssertionError(); }
            catch (HostBindings.ContractFailure | NoSuchFieldException expected) { }
        }
        if (HostBindings.uniqueField(Empty.class, String.class).get(new Empty()) != null) throw new AssertionError();
        System.out.println("VoiceLibraryBinding: 6 production shared-contract checks passed");
    }
}
