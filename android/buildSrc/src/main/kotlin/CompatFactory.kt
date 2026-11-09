import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * NewPipe Extractor, Android'de API 33'e kadar bulunmayan Java 10 metotlarını çağırıyor
 * (URLDecoder.decode(String, Charset) gibi). Bu çağrıları derleme sırasında
 * com.mekronize.app.net.Compat içindeki eşdeğerlerine yönlendiririz.
 */
abstract class CompatFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData) = classData.className.startsWith("org.schabi.newpipe.extractor")

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(opcode: Int, owner: String, mName: String, mDesc: String, isInterface: Boolean) {
                        if (opcode == Opcodes.INVOKESTATIC && mDesc == "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;") {
                            if (owner == "java/net/URLDecoder" && mName == "decode") {
                                super.visitMethodInsn(opcode, "com/mekronize/app/net/Compat", "urlDecode", mDesc, false); return
                            }
                            if (owner == "java/net/URLEncoder" && mName == "encode") {
                                super.visitMethodInsn(opcode, "com/mekronize/app/net/Compat", "urlEncode", mDesc, false); return
                            }
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                    }
                }
            }
        }
}
