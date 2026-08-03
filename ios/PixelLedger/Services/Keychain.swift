import Foundation
import Security

enum Keychain {
    static func set(_ value: String, for key: String) { let data = Data(value.utf8); remove(key); SecItemAdd([kSecClass: kSecClassGenericPassword, kSecAttrAccount: key, kSecValueData: data] as CFDictionary, nil) }
    static func value(for key: String) -> String? { var result: CFTypeRef?; let status = SecItemCopyMatching([kSecClass:kSecClassGenericPassword,kSecAttrAccount:key,kSecReturnData:true] as CFDictionary,&result); guard status == errSecSuccess, let data=result as? Data else{return nil}; return String(data:data,encoding:.utf8) }
    static func remove(_ key: String) { SecItemDelete([kSecClass:kSecClassGenericPassword,kSecAttrAccount:key] as CFDictionary) }
}
