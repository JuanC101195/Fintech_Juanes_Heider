// Tipos del cuestionario. Las preguntas son datos (preguntas.ts); las respuestas se guardan en el
// navegador y se exportan a Excel con códigos estables para que el importador del motor las lea.

export interface Opcion {
  /** Código estable que viaja en el Excel y lee el importador. */
  codigo: string
  texto: string
  detalle?: string
  /** La opción que el Excel de casos aplica hoy. */
  esExcel?: boolean
  /** Ajustes que esta respuesta produce en el motor (clave de ReglasNegocio = valor). */
  ajustes?: Record<string, string>
  /** Si la opción necesita un valor (monto, porcentaje). */
  pideValor?: boolean
  /** Si la opción necesita explicación en texto. */
  pideTexto?: boolean
  /** Opción del archivo de escenarios con que se previsualiza. */
  escenario?: string
}

export interface CampoValor {
  etiqueta: string
  unidad: 'COP' | '%' | 'pp' | 'días' | 'semanas'
  ayuda?: string
  /** Valores sugeridos; si tienen escenario, se previsualizan con el motor. */
  sugeridos?: { valor: number; escenario?: string }[]
  /** Clave de ReglasNegocio donde va el valor. */
  ajuste?: string
}

export interface Pregunta {
  id: string
  bloque: string
  titulo: string
  /** Por qué importa: qué cambia en el dinero del cliente o de la financiera. */
  contexto: string
  /** Qué hace hoy el Excel de casos. */
  excelHoy: string
  opciones: Opcion[]
  valor?: CampoValor
  /** Pregunta del archivo de escenarios que muestra el efecto de cada opción. */
  previsualizacion?: string
}

export interface Respuesta {
  codigo?: string
  valor?: number
  texto?: string
  comentario?: string
}

export interface Respondente {
  nombre: string
  cargo: string
}
