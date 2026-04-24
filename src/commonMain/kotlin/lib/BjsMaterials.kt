@file:JsModule("@babylonjs/materials")
@file:JsNonModule

package lib

/**
 * The PBRCustomMaterial allows creating custom PBR-based materials by extending the PBRMaterial
 * and providing custom shader code snippets.
 * 
 * This material can be used to create advanced visual effects while maintaining the physically-based
 * rendering properties of the standard PBRMaterial.
 */
external class PBRCustomMaterial(name: String, scene: Scene) : PBRMaterial {
    /**
     * Sets the custom shader code that will be executed before the fragment color is computed.
     * This can be used to modify the fragment color or perform additional calculations.
     *
     * @param shaderCode The GLSL shader code to execute before the fragment color
     */
    fun Fragment_Before_FragColor(shaderCode: String)

    /**
     * Sets the custom shader code that will be executed at the beginning of the fragment shader.
     * This can be used to define custom variables or functions.
     * 
     * @param shaderCode The GLSL shader code to execute at the beginning of the fragment shader
     */
    fun Fragment_Begin(shaderCode: String)
    
    /**
     * Sets the custom shader code that will be executed at the beginning of the main function in the fragment shader.
     * 
     * @param shaderCode The GLSL shader code to execute at the beginning of the main function
     */
    fun Fragment_MainBegin(shaderCode: String)

    fun Fragment_Definitions(shaderCode: String)

    /**
     * Sets the custom shader code that will be executed at the end of the fragment shader.
     * 
     * @param shaderCode The GLSL shader code to execute at the end of the fragment shader
     */
    fun Fragment_End(shaderCode: String)
    
    /**
     * Sets the custom shader code that will be executed at the beginning of the vertex shader.
     * 
     * @param shaderCode The GLSL shader code to execute at the beginning of the vertex shader
     */
    fun Vertex_Begin(shaderCode: String)
    
    /**
     * Sets the custom shader code that will be executed at the beginning of the main function in the vertex shader.
     * 
     * @param shaderCode The GLSL shader code to execute at the beginning of the main function
     */
    fun Vertex_MainBegin(shaderCode: String)
    
    /**
     * Sets the custom shader code that will be executed at the end of the vertex shader.
     * 
     * @param shaderCode The GLSL shader code to execute at the end of the vertex shader
     */
    fun Vertex_End(shaderCode: String)
    
    /**
     * Sets the custom shader code that will be executed during alpha computation.
     * This can be used to modify the alpha value of the fragment.
     * The variable to modify in this hook is 'result'.
     * 
     * @param shaderCode The GLSL shader code to execute during alpha computation
     */
    fun Fragment_Custom_Alpha(shaderCode: String)
    
    /**
     * Adds a uniform to the shader.
     * This method registers a uniform variable that can be used in the shader code.
     * 
     * @param name The name of the uniform
     * @param kind The type of the uniform (e.g., "float", "vec2", "vec3", "vec4", "mat4")
     * @param value The initial value of the uniform (optional)
     */
    fun AddUniform(name: String, kind: String, value: dynamic = definedExternally)
}

/**
 * The CustomMaterial allows creating custom StandardMaterial-based materials by extending the StandardMaterial
 * and providing custom shader code snippets (for non-PBR pipelines).
 */
external class CustomMaterial(name: String, scene: Scene) : StandardMaterial {
    fun AddUniform(name: String, kind: String)
    fun Fragment_Definitions(shaderCode: String)
    fun Vertex_Begin(shaderCode: String)
    fun Fragment_Begin(shaderCode: String)
    fun Fragment_MainEnd(shaderCode: String)
}
