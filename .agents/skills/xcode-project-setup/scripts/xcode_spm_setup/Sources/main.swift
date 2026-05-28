import Foundation
import XcodeProj
import PathKit

func isUserScriptSandboxingEnabled(project: PBXProj) -> Bool {
    guard let target = project.projects.first else {
        print("Error: No project targets found")
        return false
    }

    for configuration in target.buildConfigurationList?.buildConfigurations ?? [] {
        if let userSandbox = configuration.buildSettings["ENABLE_USER_SCRIPT_SANDBOXING"] as? String {
            return userSandbox.uppercased() == "YES"
        }
    }

    // If the value is absent, assume it is the default "YES"
    return true
}

func hasCrashlyticsRunScriptBuildPhase(project: PBXProj) -> Bool {
    guard let nativeTargets = project.nativeTargets.first else {
        return false
    }

    for phase in nativeTargets.buildPhases {
        if phase.buildPhase == BuildPhase.runScript, let scriptPhase = phase as? PBXShellScriptBuildPhase {
            if let script = scriptPhase.shellScript, script.contains("Crashlytics") {
                return true
            }
        }
    }

    return false
}

func addCrashlyticsRunScriptBuildPhase(project: PBXProj) {
    guard let nativeTarget = project.nativeTargets.first else {
        print("Error: couldn't add the Crashlytics Run Script Build phase automatically, please add it manually")
        return
    }

    var inputPaths = [
        "${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}",
        "${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${PRODUCT_NAME}",
        "${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Info.plist",
        "$(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/GoogleService-Info.plist",
        "$(TARGET_BUILD_DIR)/$(EXECUTABLE_PATH)"
    ]

    if isUserScriptSandboxingEnabled(project: project) {
        inputPaths.append("${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${PRODUCT_NAME}.debug.dylib")
    }

    let phase = PBXShellScriptBuildPhase(
        files: [],
        inputPaths: inputPaths,
        outputPaths: [],
        shellPath: "/bin/sh",
        shellScript: "\"${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run\"\n",
        runOnlyForDeploymentPostprocessing: false
    )

    project.add(object: phase)
    nativeTarget.buildPhases.append(phase)
}

func setDwarfWithDsymDebugInformationFormat(project: PBXProj) {
    guard let target = project.projects.first else {
        print("Error: No project targets found")
        return
    }

    for configuration in target.buildConfigurationList?.buildConfigurations ?? [] {
        // Set debug format for all configs
        configuration.buildSettings["DEBUG_INFORMATION_FORMAT"] = "dwarf-with-dsym"
    }
}

func main() {
    let args = CommandLine.arguments
    guard args.count >= 5 else {
        print("Usage: swift run --package-path <path> xcode_spm_setup <Path/To/Project.xcodeproj> <RepoURL> <VersionRequirement> [--plist <Path/To/Plist>] <Product1> [Product2 ...]")
        exit(1)
    }

    var arguments = args
    _ = arguments.removeFirst() // executable name
    let projectPath = Path(arguments.removeFirst())
    let repoURL = arguments.removeFirst()
    let versionRequirementString = arguments.removeFirst()
    
    var plistPath: Path? = nil
    if let plistIndex = arguments.firstIndex(of: "--plist"), plistIndex + 1 < arguments.count {
        plistPath = Path(arguments[plistIndex + 1])
        arguments.remove(at: plistIndex + 1)
        arguments.remove(at: plistIndex)
    }

    let products = arguments

    guard !products.isEmpty else {
        print("Error: No products specified to link.")
        exit(1)
    }

    do {
        let xcodeproj = try XcodeProj(path: projectPath)
        let pbxproj = xcodeproj.pbxproj
        
        guard let rootObject = try pbxproj.rootProject() else {
            print("Error: Could not find root project")
            exit(1)
        }
        
        guard let target = pbxproj.nativeTargets.first else {
            print("Error: No native targets found")
            exit(1)
        }
        
        // 1. Add Plist to the project (Optional)
        if let plistPath = plistPath {
            print("Adding \(plistPath.lastComponent) to project...")
            let mainGroup = rootObject.mainGroup
            
            let appName = target.name
            let groupToAddTo = mainGroup?.children.first(where: { $0.path == appName }) as? PBXGroup ?? mainGroup
            
            // Only add if it doesn't already exist
            if groupToAddTo?.children.contains(where: { $0.path == plistPath.lastComponent || $0.name == plistPath.lastComponent }) == false {
                let fileRef = try groupToAddTo?.addFile(at: plistPath, sourceRoot: projectPath.parent())
                
                if let fileRef = fileRef, let buildPhase = target.buildPhases.first(where: { $0.buildPhase == .resources }) as? PBXResourcesBuildPhase {
                    _ = try buildPhase.add(file: fileRef)
                    print("Successfully added \(plistPath.lastComponent) to resources build phase.")
                }
            } else {
                print("\(plistPath.lastComponent) already exists in project.")
            }
        }
        
        // 2. Add Swift Package Dependency
        print("Adding Swift Package Dependency: \(repoURL)")
        
        // Check if package already exists
        let packageRef: XCRemoteSwiftPackageReference
        if let existingPkg = rootObject.remotePackages.first(where: { $0.repositoryURL == repoURL }) {
            packageRef = existingPkg
            print("Package already present.")
        } else {
            packageRef = try rootObject.addSwiftPackage(
                repositoryURL: repoURL, 
                productName: products.first!, 
                versionRequirement: .upToNextMajorVersion(versionRequirementString), 
                targetName: target.name
            )
        }
        
        // 3. Link requested products
        print("Linking products: \(products.joined(separator: ", "))")
        var frameworksBuildPhase = target.buildPhases.compactMap { $0 as? PBXFrameworksBuildPhase }.first
        if frameworksBuildPhase == nil {
            let newPhase = PBXFrameworksBuildPhase()
            pbxproj.add(object: newPhase)
            target.buildPhases.append(newPhase)
            frameworksBuildPhase = newPhase
        }
        
        for product in products {
            // Check if product is already linked
            if target.packageProductDependencies?.contains(where: { $0.productName == product }) == true {
                print("Product \(product) is already linked.")
                continue
            }
            
            let dependency = XCSwiftPackageProductDependency(productName: product, package: packageRef)
            pbxproj.add(object: dependency)
            
            if target.packageProductDependencies == nil { target.packageProductDependencies = [] }
            target.packageProductDependencies?.append(dependency)
            
            let buildFile = PBXBuildFile(product: dependency)
            pbxproj.add(object: buildFile)
            
            if frameworksBuildPhase?.files == nil { frameworksBuildPhase?.files = [] }
            frameworksBuildPhase?.files?.append(buildFile)
        }

        // 4. Add -ObjC linker flag if adding Firebase
        if products.contains(where: { $0.contains("Firebase") }) {
            print("Adding -ObjC to OTHER_LDFLAGS...")
            for configuration in target.buildConfigurationList?.buildConfigurations ?? [] {
                var otherLdFlags: [String] = []
                if let current = configuration.buildSettings["OTHER_LDFLAGS"] {
                    if let currentArray = current as? [String] {
                        otherLdFlags = currentArray
                    } else if let currentString = current as? String {
                        otherLdFlags = [currentString]
                    }
                }
                
                if !otherLdFlags.contains("-ObjC") {
                    otherLdFlags.append("-ObjC")
                    configuration.buildSettings["OTHER_LDFLAGS"] = otherLdFlags
                    print("Updated OTHER_LDFLAGS for configuration: \(configuration.name)")
                }
            }
        }

        if products.contains(where: { $0.contains("FirebaseCrashlytics")}) {
            print("Setting the debug format to DWARF with dSYMs")
            setDwarfWithDsymDebugInformationFormat(project: pbxproj)

            print("Adding the Crashlytics Run Script Build phase")
            if !hasCrashlyticsRunScriptBuildPhase(project: pbxproj) {
                addCrashlyticsRunScriptBuildPhase(project: pbxproj)
            } else {
                print("Crashlytics Run Script Build phase already exists")
            }
        }
        
        // Write changes
        try xcodeproj.write(path: projectPath)
        print("Successfully updated Xcode project!")
        
    } catch {
        print("Error: \(error)")
        exit(1)
    }
}

main()
