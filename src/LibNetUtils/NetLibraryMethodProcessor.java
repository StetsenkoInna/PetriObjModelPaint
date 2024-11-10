package LibNetUtils;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("LibNetUtils.NetLibraryMethod")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class NetLibraryMethodProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        Types typeUtils = processingEnv.getTypeUtils();
//        Elements elementUtils = processingEnv.getElementUtils();
//        for (Element element : roundEnv.getElementsAnnotatedWith(NetLibraryMethod.class)) {
//            if (element instanceof ExecutableElement) {
//                final ExecutableElement method = (ExecutableElement) element;
//                if (!method.getParameters().isEmpty()) {
//                    processingEnv.getMessager().printMessage(
//                            Diagnostic.Kind.ERROR,
//                            "Method " + method.getSimpleName() + " should have no parameters",
//                            method
//                    );
//                    return false;
//                }
//
//                if (!typeUtils.isSameType(method.getReturnType(), elementUtils.getTypeElement("PetriObj.PetriNet").asType())) {
//                    processingEnv.getMessager().printMessage(
//                            Diagnostic.Kind.ERROR,
//                            "Method " + method.getSimpleName() + " should return String",
//                            method
//                    );
//                    return false;
//                }
//            } else {
//                processingEnv.getMessager().printMessage(
//                        Diagnostic.Kind.ERROR, "Only methods can be tagged with NetLibraryMethod"
//                );
//                return false;
//            }
//        }
        return true;
    }
}
