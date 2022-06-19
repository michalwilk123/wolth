(ns wolth.validators.protocols)

(defprotocol InterceptorProto
  (entry [req])
  (leave [req]))

(defprotocol AppModuleProto)