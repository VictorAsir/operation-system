package retry;

import lombok.extern.slf4j.Slf4j;
import retry.annotation.Retry;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 包注解扫描器
 *
 * @author errorfatal89@gmail.com
 * @date 2022/03/14
 */
@Slf4j
public class PackageScanner<T> {

    private final String pkg;

    private final Class<T> annotationClazz;

    public PackageScanner(String pkg, Class<T> annotationClazz) {
        this.pkg = pkg;
        this.annotationClazz = annotationClazz;
    }

    public void scan() {
        String packageName = pkg;
        Set<Class> classSet = new HashSet<>();
        // 是否循环迭代
        boolean recursive = true;
        // 获取包的名字 并进行替换
        String packageDirName = packageName.replace('.', '/');
        // 定义一个枚举的集合 并进行循环来处理这个目录下的things
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            // 循环迭代下去
            while (dirs.hasMoreElements()) {
                // 获取下一个元素
                URL url = dirs.nextElement();
                // 得到协议的名称
                String protocol = url.getProtocol();
                // 如果是以文件的形式保存在服务器上
                if ("file".equals(protocol)) {
                    // 获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
                    File dir = new File(filePath);
                    List<File> fileList = new ArrayList<>();
                    fetchFileList(dir, fileList);
                    for (File f : fileList) {
                        String fileName = f.getAbsolutePath();
                        if (fileName.endsWith(".class")) {
                            String noSuffixFileName = fileName.substring(8 + fileName.lastIndexOf("classes"), fileName.indexOf(".class"));
                            String filePackage = noSuffixFileName.replaceAll("\\\\", ".");
                            Class clazz = Class.forName(filePackage);
                            if (null != clazz.getAnnotation(annotationClazz)) {
                                classSet.add(clazz);
                            }

                            Method[] clazzMethods = clazz.getDeclaredMethods();
                            if (clazzMethods == null || clazzMethods.length == 0) {
                                continue;
                            }
                            // 遍历取方法的注解
                            for (Method m : clazzMethods) {
                                Annotation[] methodAnnos = m.getDeclaredAnnotations();
                                if (methodAnnos == null || methodAnnos.length == 0) {
                                    continue;
                                }

                                Optional<Annotation> annotation = Stream.of(methodAnnos).filter(e -> e.getClass().equals(annotationClazz)).findFirst();
                                if (annotation.isPresent()) {
                                    // 存在则记录Class Method Annotation参数
                                    log.info("class name: <{}>, method name: <{}>, annotation: <{}>", clazz, m, annotation);
                                }

                            }
                        }
                    }
                } else if ("jar".equals(protocol)) {
                    // 如果是jar包文件
                    // 定义一个JarFile
                    JarFile jar;
                    try {
                        // 获取jar
                        System.out.println(url);
                        JarURLConnection urlConnection = (JarURLConnection) url.openConnection();
                        jar = urlConnection.getJarFile();
                        // 从此jar包 得到一个枚举类
                        Enumeration<JarEntry> entries = jar.entries();
                        // 同样的进行循环迭代
                        while (entries.hasMoreElements()) {
                            // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            // 如果是以/开头的
                            if (name.charAt(0) == '/') {
                                // 获取后面的字符串
                                name = name.substring(1);
                            }
                            // 如果前半部分和定义的包名相同
                            if (name.startsWith(packageDirName)) {
                                int idx = name.lastIndexOf('/');
                                // 如果以"/"结尾 是一个包
                                if (idx != -1) {
                                    // 获取包名 把"/"替换成"."
                                    packageName = name.substring(0, idx).replace('/', '.');
                                }
                                // 如果可以迭代下去 并且是一个包
                                // 如果是一个.class文件 而且不是目录
                                if (name.endsWith(".class") && !entry.isDirectory()) {
                                    // 去掉后面的".class" 获取真正的类名
                                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                                    try {
                                        Class aClass = Class.forName(packageName + '.' + className);
                                        Method[] clazzMethods = aClass.getDeclaredMethods();
                                        if (clazzMethods == null || clazzMethods.length == 0) {
                                            continue;
                                        }
                                        // 遍历取方法的注解
                                        for (Method m : clazzMethods) {
                                            Annotation[] methodAnnos = m.getDeclaredAnnotations();
                                            if (methodAnnos == null || methodAnnos.length == 0) {
                                                continue;
                                            }

                                            Optional<Annotation> annotation = Stream.of(methodAnnos).filter(e -> e.getClass().equals(annotationClazz)).findFirst();
                                            if (annotation.isPresent()) {
                                                // 存在则记录Class Method Annotation参数
                                                log.info("class name: <{}>, method name: <{}>, annotation: <{}>", aClass, m, annotation);
                                            }

                                        }

                                        if (null != aClass.getAnnotation(annotationClazz)) {
                                            classSet.add(aClass);
                                        }
                                    } catch (ClassNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }


    /** * 查找所有的文件 * * @param dir 路径 * @param fileList 文件集合 */
    private static void fetchFileList(File dir, List<File> fileList) {
        if (dir.isDirectory()) {
            for (File f : Objects.requireNonNull(dir.listFiles())) {
                fetchFileList(f, fileList);
            }
        } else {
            fileList.add(dir);
        }
    }

    public static void main(String[] args) {
        PackageScanner<Retry> packageScanner = new PackageScanner<>("retry", Retry.class);
        packageScanner.scan();
    }
}
