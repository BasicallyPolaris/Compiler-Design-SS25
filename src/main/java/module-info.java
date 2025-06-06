import org.jspecify.annotations.NullMarked;

@NullMarked
module edu.kit.kastel.vads.compiler {
    requires org.jspecify;
    requires java.xml;
    requires org.jgrapht.core;
    requires jdk.compiler;
}